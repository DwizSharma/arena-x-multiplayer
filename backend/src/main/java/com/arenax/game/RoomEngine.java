package com.arenax.game;

import com.arenax.game.GameTypes.*;
import com.arenax.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class RoomEngine {
    private static final int MAX_PLAYERS = 50;
    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final long INTRO_MS = 3_000, REVEAL_MS = 5_000, BOARD_MS = 5_000, JITTER_MS = 250;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService clock = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "arena-room-clock"); t.setDaemon(true); return t;
    });
    private final SimpMessagingTemplate messaging;
    private final QuestionSetService questionSets;
    private final MatchFinalizationService finalization;
    private final RoomSnapshotCache cache;
    private final DistributedRoomCoordinator coordinator;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();
    public RoomEngine(SimpMessagingTemplate messaging, QuestionSetService questionSets, RoomSnapshotCache cache,
                      MatchFinalizationService finalization,
                      DistributedRoomCoordinator coordinator) {
        this.messaging = messaging; this.questionSets=questionSets; this.cache=cache; this.finalization=finalization; this.coordinator=coordinator;
    }
    @PreDestroy public void close() { clock.shutdownNow(); }

    public void refreshFromSharedSnapshot(String code) {
        RoomStateSnapshot saved=cache.getCritical("room-state:"+code,RoomStateSnapshot.class);
        if(saved==null)return;
        Room room=rooms.computeIfAbsent(code,Room::new);
        synchronized(room) {
            room.phase=saved.phase(); room.prompts=saved.prompts(); room.mode=saved.mode(); room.questionSetId=saved.questionSetId();
            room.hostManaged=saved.hostManaged(); room.questionIndex=saved.questionIndex(); room.serverStart=saved.serverStart();
            room.endsAt=saved.endsAt(); room.allowedMs=saved.allowedMs(); room.transitionAt=saved.transitionAt();
            room.distribution.clear(); room.distribution.putAll(saved.distribution()); room.players.clear();
            room.momentId=saved.momentId(); room.momentKind=saved.momentKind(); room.momentPrompt=saved.momentPrompt();
            room.momentClosesAt=saved.momentClosesAt(); room.momentOptions=saved.momentOptions()==null?List.of():saved.momentOptions();
            room.momentCounts.clear(); if(saved.momentCounts()!=null)room.momentCounts.putAll(saved.momentCounts());
            room.momentVotes.clear(); if(saved.momentVotes()!=null)room.momentVotes.putAll(saved.momentVotes());
            for(RoomStateSnapshot.PlayerState state:saved.players()) {
                Player player=new Player(state.identity()); player.score=state.score(); player.answersCorrect=state.answersCorrect();
                player.responseTimes.addAll(state.responseTimes()); player.optionMapping.putAll(state.optionMapping());
                state.submissions().forEach((index,value)->player.submissions.put(index,new Submission(value.canonicalOptionId(),value.correct(),value.pointsEarned(),value.elapsedMs())));
                state.submissions().forEach((index,value)->{
                    if(index>=0&&index<room.prompts.size()) {
                        Prompt prompt=room.prompts.get(index);
                        player.topics.computeIfAbsent(prompt.topic()==null?"Other":prompt.topic(),ignored->new TopicCounter()).add(value.correct(),value.elapsedMs());
                    }
                });
                if(room.questionIndex>=0&&room.questionIndex<room.prompts.size()) player.displayOptions=permuteForPlayer(room.current(),player);
                room.players.put(player.identity.playerId(),player);
            }
            ensureTransitionScheduled(room);
            ensureMomentScheduled(room);
        }
    }

    @PostConstruct public void recoverRooms() {
        for (RoomStateSnapshot saved : cache.getRoomSnapshots()) {
            Room room = new Room(saved.code());
            room.phase=saved.phase(); room.prompts=saved.prompts(); room.mode=saved.mode(); room.questionSetId=saved.questionSetId();
            room.hostManaged=saved.hostManaged(); room.questionIndex=saved.questionIndex(); room.serverStart=saved.serverStart();
            room.endsAt=saved.endsAt(); room.allowedMs=saved.allowedMs(); room.transitionAt=saved.transitionAt();
            room.distribution.putAll(saved.distribution());
            room.momentId=saved.momentId(); room.momentKind=saved.momentKind(); room.momentPrompt=saved.momentPrompt();
            room.momentClosesAt=saved.momentClosesAt(); room.momentOptions=saved.momentOptions()==null?List.of():saved.momentOptions();
            if(saved.momentCounts()!=null)room.momentCounts.putAll(saved.momentCounts());
            if(saved.momentVotes()!=null)room.momentVotes.putAll(saved.momentVotes());
            for (RoomStateSnapshot.PlayerState state : saved.players()) {
                Player player=new Player(state.identity()); player.score=state.score(); player.answersCorrect=state.answersCorrect();
                player.responseTimes.addAll(state.responseTimes()); player.optionMapping.putAll(state.optionMapping());
                state.submissions().forEach((index, value) -> player.submissions.put(index,
                        new Submission(value.canonicalOptionId(),value.correct(),value.pointsEarned(),value.elapsedMs())));
                state.submissions().forEach((index, value) -> {
                    if(index>=0 && index<room.prompts.size()) {
                        Prompt prompt=room.prompts.get(index);
                        player.topics.computeIfAbsent(prompt.topic()==null?"Other":prompt.topic(),ignored->new TopicCounter())
                                .add(value.correct(),value.elapsedMs());
                    }
                });
                if(room.questionIndex>=0 && room.questionIndex<room.prompts.size())
                    player.displayOptions=permuteForPlayer(room.current(),player);
                room.players.put(player.identity.playerId(),player);
            }
            if(rooms.putIfAbsent(room.code,room)!=null) continue;
            synchronized(room) {
                switch(room.phase) {
                    case QUESTION_INTRO -> scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::activate);
                    case QUESTION_ACTIVE -> scheduleTransition(room,Math.max(0,room.endsAt+JITTER_MS-System.currentTimeMillis()),room.phase,this::reveal);
                    case QUESTION_REVEAL -> scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::leaderboard);
                    case ROUND_LEADERBOARD -> scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::advanceAfterLeaderboard);
                    default -> { }
                }
                ensureMomentScheduled(room);
                publishRoster(room); broadcastState(room);
            }
        }
        clock.scheduleAtFixedRate(this::reconcileRooms,2,2,TimeUnit.SECONDS);
    }

    private void reconcileRooms() {
        for(RoomStateSnapshot state:cache.getRoomSnapshots()) {
            try(var lock=coordinator.acquire(state.code())) {
                refreshFromSharedSnapshot(state.code());
                Room room=rooms.get(state.code()); if(room!=null) synchronized(room) { ensureTransitionScheduled(room); }
            } catch(RuntimeException error) { /* Retry transient coordination failures on the next pass. */ }
        }
    }

    private void ensureTransitionScheduled(Room room) {
        if(room.phase==Phase.QUESTION_INTRO) scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::activate);
        else if(room.phase==Phase.QUESTION_ACTIVE) scheduleTransition(room,Math.max(0,room.endsAt+JITTER_MS-System.currentTimeMillis()),room.phase,this::reveal);
        else if(room.phase==Phase.QUESTION_REVEAL) scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::leaderboard);
        else if(room.phase==Phase.ROUND_LEADERBOARD) scheduleTransition(room,Math.max(0,room.transitionAt-System.currentTimeMillis()),room.phase,this::advanceAfterLeaderboard);
        else if(room.task!=null) { room.task.cancel(false); room.task=null; room.scheduledPhase=null; room.scheduledAt=0; }
    }

    private void scheduleTransition(Room room,long delay,Phase phase,Consumer<Room> action) {
        long scheduledAt=phase==Phase.QUESTION_ACTIVE?room.endsAt+JITTER_MS:room.transitionAt;
        if(room.scheduledPhase==phase && room.scheduledAt==scheduledAt)return;
        if(room.task!=null)room.task.cancel(false);
        room.scheduledPhase=phase;room.scheduledAt=scheduledAt;
        room.task=clock.schedule(()->runTransition(room.code,phase,action),delay,TimeUnit.MILLISECONDS);
    }

    private void runTransition(String code,Phase expected,Consumer<Room> action) {
        Room room=null;
        try(var lock=coordinator.acquire(code)) {
            refreshFromSharedSnapshot(code);
            room=rooms.get(code); if(room==null)return;
            synchronized(room) {
                if(room.phase==expected) {
                    room.scheduledPhase=null; room.scheduledAt=0;
                    action.accept(room);
                }
            }
        } catch(RuntimeException error) { /* The next reconciliation pass schedules the transition again. */ }
        finally { if(room!=null) synchronized(room) { ensureTransitionScheduled(room); } }
    }

    private void ensureMomentScheduled(Room room) {
        if(room.momentId==null || room.momentId.isBlank()) { room.momentScheduledId=null; return; }
        if(room.momentId.equals(room.momentScheduledId))return;
        if(room.momentTask!=null)room.momentTask.cancel(false);
        String id=room.momentId; room.momentScheduledId=id;
        room.momentTask=clock.schedule(()->closeMoment(room.code,id),Math.max(0,room.momentClosesAt-System.currentTimeMillis()),TimeUnit.MILLISECONDS);
    }

    private void closeMoment(String code,String expectedId) {
        Room room=null;
        try(var lock=coordinator.acquire(code)) {
            refreshFromSharedSnapshot(code);
            room=rooms.get(code); if(room==null)return;
            synchronized(room) {
                if(expectedId.equals(room.momentId) && System.currentTimeMillis()>=room.momentClosesAt) {
                    messaging.convertAndSend(topic(room,"moment"),new RoomMoment(room.momentId,room.momentKind,
                            room.momentPrompt,room.momentOptions,Map.copyOf(room.momentCounts),room.momentClosesAt,false));
                    room.momentId=null; room.momentScheduledId=null; persistRoom(room);
                }
            }
        } catch(RuntimeException error) { /* Reconciliation retries moment closure. */ }
        finally { if(room!=null) synchronized(room) { if(expectedId.equals(room.momentId)&&expectedId.equals(room.momentScheduledId)) { room.momentScheduledId=null; ensureMomentScheduled(room); } } }
    }

    private void persistRoom(Room room) {
        List<RoomStateSnapshot.PlayerState> players=room.players.values().stream().map(player -> {
            Map<Integer,RoomStateSnapshot.SubmissionState> submissions=new HashMap<>();
            player.submissions.forEach((index,value)->submissions.put(index,new RoomStateSnapshot.SubmissionState(
                    value.canonicalOptionId(),value.correct(),value.pointsEarned(),value.elapsedMs())));
            return new RoomStateSnapshot.PlayerState(player.identity,player.score,player.answersCorrect,
                    List.copyOf(player.responseTimes),Map.copyOf(submissions),Map.copyOf(player.optionMapping));
        }).toList();
        cache.putCritical("room-state:"+room.code,new RoomStateSnapshot(room.code,room.phase,room.prompts,room.mode,
                room.questionSetId,room.hostManaged,room.questionIndex,room.serverStart,room.endsAt,room.allowedMs,
                room.transitionAt,Map.copyOf(room.distribution),players,room.momentId,room.momentKind,room.momentPrompt,
                room.momentClosesAt,List.copyOf(room.momentOptions),Map.copyOf(room.momentCounts),Map.copyOf(room.momentVotes)));
    }

    @SharedRoomOperation public synchronized PlayerView join(String code, SessionRegistry.Identity identity) {
        Room room = rooms.computeIfAbsent(code.toUpperCase(Locale.ROOT), Room::new);
        synchronized (room) {
            Player player = room.players.get(identity.playerId());
            if (player == null) {
                if (room.phase != Phase.LOBBY || room.players.size() >= MAX_PLAYERS) throw new IllegalStateException("Room is full or already started");
                player = new Player(identity); room.players.put(identity.playerId(), player);
            }
            publishRoster(room); return view(room, player);
        }
    }
    @SharedRoomOperation(global=true) public AdminRoomView createPrivateRoom(String mode, String questionSetId) {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 10; i++) code.append(ROOM_CODE_CHARS[secureRandom.nextInt(ROOM_CODE_CHARS.length)]);
            Room room = new Room(code.toString());
            room.hostManaged = true;
            room.mode = normalizeMode(mode);
            room.questionSetId = questionSetId == null || questionSetId.isBlank() ? null : questionSetId.trim();
            if (rooms.putIfAbsent(room.code, room) == null) { synchronized(room) { persistRoom(room); } return adminView(room); }
        }
        throw new IllegalStateException("Could not allocate a room code. Try again.");
    }
    @SharedRoomOperation public AdminRoomView adminRoom(String code) { return adminView(requireRoom(code)); }
    @SharedRoomOperation public AdminRoomView startPrivateRoom(String code) {
        Room room = requireRoom(code);
        synchronized (room) {
            if (!room.hostManaged) throw new IllegalArgumentException("This room is not host-managed");
            startLocked(room, room.questionSetId, room.mode, true);
            return adminView(room);
        }
    }
    @SharedRoomOperation public RoomMoment launchMoment(String code, String requestedKind) {
        Room room = requireRoom(code);
        synchronized (room) {
            if (room.phase == Phase.LOBBY || room.phase == Phase.FINAL_PODIUM)
                throw new IllegalStateException("Fun moments are available while a match is underway");
            String kind = requestedKind == null ? "HYPE_CHECK" : requestedKind.trim().toUpperCase(Locale.ROOT);
            String prompt;
            List<String> options;
            switch (kind) {
                case "MEME_BREAK" -> { prompt = "Meme break: choose the placement-prep mood."; options = List.of("Locked in 🔒", "Need a snack 🍿", "Send help 🫠"); }
                case "BRAIN_BREAK" -> { prompt = "Quick reset: what should the squad do after this round?"; options = List.of("Stretch break 🧘", "One more round 🎮", "Victory snack 🍕"); }
                case "HYPE_CHECK" -> { prompt = "Hype check: how is the room feeling?"; options = List.of("We are cooking 🔥", "Steady focus 🎯", "Plot twist incoming 👀"); }
                default -> throw new IllegalArgumentException("Choose a supported fun moment");
            }
            if (room.momentTask != null) room.momentTask.cancel(false);
            room.momentCounts.clear();
            room.momentVotes.clear();
            room.momentId = UUID.randomUUID().toString(); room.momentKind = kind; room.momentPrompt = prompt;
            options.forEach(option -> room.momentCounts.put(option, 0));
            room.momentClosesAt = System.currentTimeMillis() + 12_000;
            RoomMoment moment = momentView(room);
            persistRoom(room); ensureMomentScheduled(room);
            messaging.convertAndSend(topic(room, "moment"), moment);
            return moment;
        }
    }
    @SharedRoomOperation public void voteMoment(String code, String playerId, MomentVote vote) {
        Room room = requireRoom(code);
        synchronized (room) {
            if (vote == null || vote.choice() == null || room.momentId == null || !room.momentId.equals(vote.momentId())
                    || System.currentTimeMillis() > room.momentClosesAt) return;
            if (!room.players.containsKey(playerId) || !room.momentCounts.containsKey(vote.choice())) return;
            if (room.momentVotes.putIfAbsent(playerId, vote.choice()) == null) {
                room.momentCounts.merge(vote.choice(), 1, Integer::sum);
                persistRoom(room);
                messaging.convertAndSend(topic(room, "moment"), momentView(room));
            }
        }
    }
    private RoomMoment momentView(Room room) {
        return new RoomMoment(room.momentId, room.momentKind, room.momentPrompt, room.momentOptions,
                Map.copyOf(room.momentCounts), room.momentClosesAt, System.currentTimeMillis() <= room.momentClosesAt);
    }
    @SharedRoomOperation public void watch(String code) {
        Room room = requireRoom(code);
        synchronized (room) { publishRoster(room); broadcastState(room); }
    }
    @SharedRoomOperation public void start(String code, String questionSetId, String requestedMode) {
        Room room = requireRoom(code);
        synchronized (room) {
            startLocked(room, questionSetId, requestedMode, false);
        }
    }
    private void startLocked(Room room, String questionSetId, String requestedMode, boolean adminStart) {
        if (room.hostManaged && !adminStart) throw new IllegalStateException("The room host controls when this match starts");
        if (room.players.isEmpty()) throw new IllegalStateException("At least one student must join before starting");
        if (room.phase != Phase.LOBBY) throw new IllegalStateException("Room has already started");
        room.mode = normalizeMode(requestedMode);
        String effectiveSet = questionSetId == null || questionSetId.isBlank() ? room.questionSetId : questionSetId;
        room.prompts = effectiveSet == null || effectiveSet.isBlank() ? samplePrompts(room.mode) : questionSets.loadPrompts(effectiveSet);
        room.questionIndex = 0; intro(room);
    }
    private AdminRoomView adminView(Room room) {
        synchronized (room) { return new AdminRoomView(room.code, room.mode, room.phase.name(), room.players.size(), MAX_PLAYERS, "/play/" + room.code + "?mode=" + room.mode); }
    }
    @SharedRoomOperation public void submit(String code, String playerId, Answer answer) {
        Room room = requireRoom(code); long received = System.currentTimeMillis();
        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null) { sendPlayer(playerId, "answer-result", new SubmissionResult("UNKNOWN_PLAYER", 0, 0)); return; }
            if (room.phase != Phase.QUESTION_ACTIVE || answer == null || !room.current().id().equals(answer.questionId())) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("NOT_ACTIVE", 0, 0)); return;
            }
            if (received > room.endsAt + JITTER_MS) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("LATE_SUBMISSION", 0, received-room.serverStart)); return;
            }
            if (!player.optionMapping.containsKey(answer.optionId())) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("INVALID_OPTION", 0, received-room.serverStart)); return;
            }
            String canonicalOptionId = player.optionMapping.get(answer.optionId());
            if (player.submissions.containsKey(room.questionIndex)) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("DUPLICATE", 0, received-room.serverStart)); return;
            }
            long elapsed = Math.max(0, received - room.serverStart);
            boolean correct = room.current().correctOptionId().equals(canonicalOptionId);
            int score = AnswerScorer.score(correct, elapsed, room.allowedMs);
            Submission submission = new Submission(canonicalOptionId, correct, score, elapsed);
            if (player.submissions.putIfAbsent(room.questionIndex, submission) != null) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("DUPLICATE", 0, received-room.serverStart)); return;
            }
            player.score += score; player.answersCorrect += correct ? 1 : 0; player.responseTimes.add(elapsed);
            String topic = room.current().topic() == null ? "Other" : room.current().topic();
            player.topics.computeIfAbsent(topic, ignored -> new TopicCounter()).add(correct, elapsed);
            room.distribution.merge(player.optionMapping.get(answer.optionId()), 1, Integer::sum);
            sendPlayer(playerId, "answer-result", new SubmissionResult("ACCEPTED", score, elapsed));
            publishRoster(room);
            messaging.convertAndSend(topic(room, "progress"), Map.of("submitted", countSubmitted(room), "total", room.players.size(),
                    "distribution", Map.copyOf(room.distribution)));
            if (countSubmitted(room) == room.players.size()) reveal(room);
        }
    }
    @SharedRoomOperation public Snapshot snapshot(String code, String playerId) {
        Room room = requireRoom(code);
        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null) throw new IllegalArgumentException("Player is not in room");
            List<Option> options = room.phase == Phase.QUESTION_ACTIVE || room.phase == Phase.QUESTION_INTRO ? player.displayOptions : List.of();
            long end = room.phase == Phase.QUESTION_ACTIVE ? room.endsAt : room.transitionAt;
            PublicQuestion currentQuestion = room.phase == Phase.QUESTION_ACTIVE ? new PublicQuestion(room.questionIndex,
                    room.current().id(), room.current().prompt(), options, room.current().topic(), room.serverStart,
                    room.endsAt, room.allowedMs, Math.max(0, room.endsAt-System.currentTimeMillis()),
                    room.current().imageUrl(), room.current().tableData()) : null;
            return new Snapshot(room.code, room.phase, room.mode, room.hostManaged, room.questionIndex,
                    end == 0 ? 0 : Math.max(0, end - System.currentTimeMillis()), options, currentQuestion,
                    player.submissions.containsKey(room.questionIndex), player.score, rank(room, player), standings(room));
        }
    }
    private void intro(Room room) {
        room.phase = Phase.QUESTION_INTRO; room.transitionAt = System.currentTimeMillis() + INTRO_MS;
        for (Player player : room.players.values()) permuteForPlayer(room.current(), player);
        broadcastState(room); scheduleTransition(room,INTRO_MS,Phase.QUESTION_INTRO,this::activate);
    }
    private void activate(Room room) {
        room.phase = Phase.QUESTION_ACTIVE; room.serverStart = System.currentTimeMillis();
        room.allowedMs = Math.max(20, Math.min(45, room.current().durationSeconds())) * 1000L;
        room.endsAt = room.serverStart + room.allowedMs; room.transitionAt = room.endsAt;
        room.distribution.clear(); publishRoster(room); broadcastState(room);
        messaging.convertAndSend(topic(room, "progress"), Map.of("submitted", 0, "total", room.players.size(), "distribution", Map.of()));
        PublicQuestion publicQuestion = new PublicQuestion(room.questionIndex, room.current().id(), room.current().prompt(),
                room.current().options(), room.current().topic(), room.serverStart, room.endsAt, room.allowedMs, room.allowedMs,
                room.current().imageUrl(), room.current().tableData());
        messaging.convertAndSend(topic(room, "question"), publicQuestion);
        cache.put("room:" + room.code + ":question", publicQuestion);
        for (Player player : room.players.values()) {
            List<Option> shuffle = permuteForPlayer(room.current(), player);
            sendPlayer(player.identity.playerId(), "question", new PublicQuestion(room.questionIndex, room.current().id(), room.current().prompt(),
                    shuffle, room.current().topic(), room.serverStart, room.endsAt, room.allowedMs, room.allowedMs,
                    room.current().imageUrl(), room.current().tableData()));
        }
        scheduleTransition(room,room.allowedMs+JITTER_MS,Phase.QUESTION_ACTIVE,this::reveal);
    }
    private List<Option> shuffled(Prompt prompt, Player player) {
        List<Option> options = new ArrayList<>(prompt.options());
        long seed = Objects.hash(player.identity.playerId(), prompt.id()); Collections.shuffle(options, new Random(seed));
        return options;
    }
    private List<Option> permuteForPlayer(Prompt prompt, Player player) {
        List<Option> shuffled = shuffled(prompt, player);
        List<Option> presented = new ArrayList<>(shuffled.size());
        player.optionMapping.clear();
        for (int i=0; i<shuffled.size(); i++) {
            String presentedId=String.valueOf((char)('A'+i));
            Option canonical=shuffled.get(i);
            player.optionMapping.put(presentedId, canonical.id());
            presented.add(new Option(presentedId, canonical.text()));
        }
        player.displayOptions=List.copyOf(presented);
        return player.displayOptions;
    }
    private void reveal(Room room) {
        if (room.task != null) room.task.cancel(false);
        room.phase = Phase.QUESTION_REVEAL; room.transitionAt = System.currentTimeMillis() + REVEAL_MS;
        String correctText=room.current().options().stream().filter(option->option.id().equals(room.current().correctOptionId()))
                .map(Option::text).findFirst().orElse("");
        messaging.convertAndSend(topic(room, "reveal"), Map.of("questionIndex", room.questionIndex,
                "correctOptionId", room.current().correctOptionId(), "correctText", correctText,
                "distribution", Map.copyOf(room.distribution), "standings", standings(room)));
        broadcastState(room);
        scheduleTransition(room,REVEAL_MS,Phase.QUESTION_REVEAL,this::leaderboard);
    }
    private void leaderboard(Room room) {
        room.phase = Phase.ROUND_LEADERBOARD; room.transitionAt = System.currentTimeMillis() + BOARD_MS;
        messaging.convertAndSend(topic(room, "leaderboard"), standings(room)); broadcastState(room);
        scheduleTransition(room,BOARD_MS,Phase.ROUND_LEADERBOARD,this::advanceAfterLeaderboard);
    }
    private void advanceAfterLeaderboard(Room room) {
        if (room.phase != Phase.ROUND_LEADERBOARD) return;
        if (++room.questionIndex >= room.prompts.size()) {
            int finalIndex=room.questionIndex;
            long previousTransition=room.transitionAt;
            room.phase = Phase.FINAL_PODIUM; room.transitionAt = 0;
            try { persistMatch(room); }
            catch(RuntimeException error) {
                room.phase=Phase.ROUND_LEADERBOARD; room.questionIndex=finalIndex-1; room.transitionAt=previousTransition;
                throw error;
            }
            messaging.convertAndSend(topic(room, "results"), standings(room)); broadcastState(room);
        } else intro(room);
    }
    private void broadcastState(Room room) {
        Map<String,Object> state=Map.of("phase", room.phase, "questionIndex", room.questionIndex,
                "remainingMs", Math.max(0, room.transitionAt-System.currentTimeMillis()), "mode", room.mode,
                "hostManaged", room.hostManaged);
        messaging.convertAndSend(topic(room, "state"), state); cache.put("room:" + room.code + ":state", state);
        persistRoom(room);
    }
    private void publishRoster(Room room) {
        List<PlayerView> roster=standings(room); messaging.convertAndSend(topic(room, "roster"), roster);
        cache.put("room:" + room.code + ":roster", roster);
        persistRoom(room);
    }
    private List<PlayerView> standings(Room room) { List<Player> sorted = new ArrayList<>(room.players.values()); sorted.sort(Comparator.comparingInt((Player p)->p.score).reversed().thenComparing(p->p.identity.name())); List<PlayerView> views=new ArrayList<>(); for(int i=0;i<sorted.size();i++) { Player p=sorted.get(i); int answered=p.responseTimes.size(); long avg=answered==0?0:p.responseTimes.stream().mapToLong(Long::longValue).sum()/answered; int accuracyDenominator=room.phase==Phase.FINAL_PODIUM?room.prompts.size():answered; Map<String,TopicAnalytics> topics=new TreeMap<>(); p.topics.forEach((name,counter)->topics.put(name,counter.view())); views.add(new PlayerView(p.identity.playerId(),p.identity.name(),p.identity.college(),p.score,i+1,p.submissions.containsKey(room.questionIndex),accuracyDenominator==0?0:p.answersCorrect*100/accuracyDenominator,avg,topics)); } return views; }
    private PlayerView view(Room room, Player player) { return standings(room).stream().filter(v->v.playerId().equals(player.identity.playerId())).findFirst().orElseThrow(); }
    private int rank(Room room, Player player) { return view(room, player).rank(); }
    private int countSubmitted(Room room) { return (int)room.players.values().stream().filter(p->p.submissions.containsKey(room.questionIndex)).count(); }
    private void persistMatch(Room room) {
        List<Player> sorted = new ArrayList<>(room.players.values());
        sorted.sort(Comparator.comparingInt((Player p)->p.score).reversed().thenComparing(p->p.identity.name()));
        String winner = sorted.isEmpty() ? null : sorted.get(0).identity.playerId();
        Map<String,Long> pointsByCollege = new HashMap<>();
        room.players.values().forEach(p -> pointsByCollege.merge(p.identity.college(), (long)p.score, Long::sum));
        List<PlayerView> finalStandings = standings(room);
        Map<String,Integer> ranks = new HashMap<>();
        finalStandings.forEach(player -> ranks.put(player.playerId(), player.rank()));
        List<PlayerAnalyticsService.PlayerInput> durableResults = room.players.values().stream().map(player -> {
            PlayerView view = finalStandings.stream().filter(candidate -> candidate.playerId().equals(player.identity.playerId())).findFirst().orElseThrow();
            List<PlayerAnalyticsService.AnswerInput> answerRows = new ArrayList<>();
            for (int index=0; index<room.prompts.size(); index++) {
                Prompt prompt = room.prompts.get(index);
                Submission submission = player.submissions.get(index);
                answerRows.add(new PlayerAnalyticsService.AnswerInput(index, prompt.id(), prompt.prompt(), prompt.topic(),
                        submission == null ? null : submission.canonicalOptionId(), submission != null && submission.correct(),
                        submission == null ? 0 : submission.pointsEarned(), submission == null ? null : submission.elapsedMs()));
            }
            return new PlayerAnalyticsService.PlayerInput(player.identity.playerId(), player.identity.name(), player.identity.college(),
                    player.score, ranks.get(player.identity.playerId()), player.answersCorrect, room.prompts.size(),
                    view.accuracyPercent(), view.averageSpeedMs(), view.topics(), answerRows);
        }).toList();
        finalization.finalizeMatch(room.code,winner,pointsByCollege,durableResults);
    }
    private String topic(Room room, String event) { return "/topic/room/"+room.code+"/"+event; }
    private void sendPlayer(String playerId, String queue, Object payload) { messaging.convertAndSendToUser(playerId, "/queue/"+queue, payload); }
    private Room requireRoom(String code) { Room room=rooms.get(code.toUpperCase(Locale.ROOT)); if(room==null) throw new IllegalArgumentException("Unknown room"); return room; }
    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "CLASSIC";
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        return Set.of("CLASSIC", "MEME_RUSH", "QUANT_SPRINT", "LOGIC_LAB").contains(normalized) ? normalized : "CLASSIC";
    }
    private List<Prompt> samplePrompts(String mode) {
        return switch (mode) {
            case "MEME_RUSH" -> List.of(
                new Prompt("meme-1","POV: the placement test asks for 15% of 240. Your calculator is on a coffee break. **What is it?**",List.of(new Option("A","24"),new Option("B","36"),new Option("C","40"),new Option("D","48")),"B","Quant",20,null,List.of()),
                new Prompt("meme-2","The group project has 4 people and 18 tasks. If work is split equally, how many tasks per person? No side quests.",List.of(new Option("A","3"),new Option("B","4"),new Option("C","4.5"),new Option("D","6")),"C","Quant",20,null,List.of()),
                new Prompt("meme-3","A train meme says: 90 km in 1.5 hours. The train is somehow on time. What is its average speed?",List.of(new Option("A","45 km/h"),new Option("B","60 km/h"),new Option("C","75 km/h"),new Option("D","90 km/h")),"B","Quant",20,null,List.of()),
                new Prompt("meme-4","The sequence is 3, 6, 12, 24, __. The pattern is doing all the work here.",List.of(new Option("A","30"),new Option("B","36"),new Option("C","42"),new Option("D","48")),"D","Logical",20,null,List.of()),
                new Prompt("meme-5","Choose the closest meaning of **resilient**. Main-character comeback energy.",List.of(new Option("A","Fragile"),new Option("B","Adaptable"),new Option("C","Careless"),new Option("D","Ordinary")),"B","Verbal",20,null,List.of()));
            case "QUANT_SPRINT" -> List.of(
                new Prompt("quant-1","A product marked ₹800 gets a 15% discount. What is the sale price?",List.of(new Option("A","₹620"),new Option("B","₹660"),new Option("C","₹680"),new Option("D","₹720")),"C","Quant",20,null,List.of()),
                new Prompt("quant-2","A car covers 150 km in 2.5 hours. Find its average speed.",List.of(new Option("A","50 km/h"),new Option("B","55 km/h"),new Option("C","60 km/h"),new Option("D","65 km/h")),"C","Quant",20,null,List.of()),
                new Prompt("quant-3","The ratio of two numbers is 3:5 and their sum is 64. What is the smaller number?",List.of(new Option("A","18"),new Option("B","24"),new Option("C","32"),new Option("D","40")),"B","Quant",20,null,List.of()),
                new Prompt("quant-4","A can finish a task in 12 days. At the same rate, what fraction is done in 3 days?",List.of(new Option("A","1/2"),new Option("B","1/3"),new Option("C","1/4"),new Option("D","1/6")),"C","Quant",20,null,List.of()),
                new Prompt("quant-5","What is the simple interest on ₹2,000 at 5% per year for 2 years?",List.of(new Option("A","₹100"),new Option("B","₹150"),new Option("C","₹200"),new Option("D","₹250")),"C","Quant",20,null,List.of()));
            case "LOGIC_LAB" -> List.of(
                new Prompt("logic-1","Find the next number: 2, 6, 12, 20, ?",List.of(new Option("A","24"),new Option("B","28"),new Option("C","30"),new Option("D","32")),"C","Logical",20,null,List.of()),
                new Prompt("logic-2","All Bloops are Razzies. All Razzies are Lazzies. Which statement must be true?",List.of(new Option("A","All Bloops are Lazzies"),new Option("B","All Lazzies are Bloops"),new Option("C","No Bloops are Lazzies"),new Option("D","Some Razzies are not Bloops")),"A","Logical",20,null,List.of()),
                new Prompt("logic-3","If yesterday was two days before Friday, what day is tomorrow?",List.of(new Option("A","Thursday"),new Option("B","Friday"),new Option("C","Saturday"),new Option("D","Sunday")),"B","Logical",20,null,List.of()),
                new Prompt("logic-4","Choose the odd one out: square, triangle, circle, cube.",List.of(new Option("A","Square"),new Option("B","Triangle"),new Option("C","Circle"),new Option("D","Cube")),"D","Logical",20,null,List.of()),
                new Prompt("logic-5","A is taller than B. B is taller than C. Who is shortest?",List.of(new Option("A","A"),new Option("B","B"),new Option("C","C"),new Option("D","Cannot tell")),"C","Logical",20,null,List.of()));
            default -> List.of(
                new Prompt("apt-1","A train travels 120 km in 2 hours. What is its average speed?",List.of(new Option("A","40 km/h"),new Option("B","50 km/h"),new Option("C","60 km/h"),new Option("D","80 km/h")),"C","Quant",20,null,List.of()),
                new Prompt("apt-2","Choose the word closest in meaning to concise.",List.of(new Option("A","Brief"),new Option("B","Vague"),new Option("C","Loud"),new Option("D","Formal")),"A","Verbal",25,null,List.of()),
                new Prompt("apt-3","Find the next number: 2, 6, 12, 20, ?",List.of(new Option("A","24"),new Option("B","28"),new Option("C","30"),new Option("D","32")),"C","Logical",30,null,List.of()));
        };
    }

    private static final class Room {
        final String code; final Map<String,Player> players=new ConcurrentHashMap<>(); Phase phase=Phase.LOBBY;
        List<Prompt> prompts=List.of(); String mode="CLASSIC", questionSetId; boolean hostManaged; int questionIndex; long serverStart, endsAt, allowedMs, transitionAt;
        String momentId, momentKind, momentPrompt; long momentClosesAt; List<String> momentOptions=List.of();
        final Map<String,Integer> momentCounts=new ConcurrentHashMap<>(); final Map<String,String> momentVotes=new ConcurrentHashMap<>(); ScheduledFuture<?> momentTask;
        ScheduledFuture<?> task; Phase scheduledPhase; long scheduledAt; String momentScheduledId; final Map<String,Integer> distribution=new HashMap<>();
        Room(String code) { this.code=code; }
        Prompt current() { return prompts.get(questionIndex); }
    }
    private static final class Player {
        final SessionRegistry.Identity identity; int score, answersCorrect; final List<Long> responseTimes=new ArrayList<>();
        final Map<Integer,Submission> submissions=new ConcurrentHashMap<>(); final Map<String,String> optionMapping=new ConcurrentHashMap<>();
        List<Option> displayOptions=List.of();
        final Map<String,TopicCounter> topics=new ConcurrentHashMap<>();
        Player(SessionRegistry.Identity identity) { this.identity=identity; }
    }
    private record Submission(String canonicalOptionId, boolean correct, int pointsEarned, long elapsedMs) {}
    private static final class TopicCounter {
        int answered, correct; long totalSpeed;
        synchronized void add(boolean isCorrect, long speed) { answered++; if(isCorrect) correct++; totalSpeed += speed; }
        synchronized TopicAnalytics view() { return new TopicAnalytics(answered,correct,answered==0?0.0:(double)correct*100/answered,answered==0?0:totalSpeed/answered); }
    }
}
