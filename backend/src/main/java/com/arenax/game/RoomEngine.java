package com.arenax.game;

import com.arenax.game.GameTypes.*;
import com.arenax.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

@Component
public class RoomEngine {
    private static final int MAX_PLAYERS = 50;
    private static final long INTRO_MS = 3_000, REVEAL_MS = 5_000, BOARD_MS = 5_000, JITTER_MS = 250;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService clock = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "arena-room-clock"); t.setDaemon(true); return t;
    });
    private final SimpMessagingTemplate messaging;
    private final CollegeRepository colleges;
    private final MatchHistoryRepository matches;
    private final QuestionSetService questionSets;
    private final RoomSnapshotCache cache;
    public RoomEngine(SimpMessagingTemplate messaging, CollegeRepository colleges, MatchHistoryRepository matches,
                      QuestionSetService questionSets, RoomSnapshotCache cache) {
        this.messaging = messaging; this.colleges = colleges; this.matches = matches; this.questionSets=questionSets; this.cache=cache;
    }
    @PreDestroy public void close() { clock.shutdownNow(); }

    public synchronized PlayerView join(String code, SessionRegistry.Identity identity) {
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
    public void watch(String code) {
        Room room = requireRoom(code);
        synchronized (room) { publishRoster(room); broadcastState(room); }
    }
    public void start(String code, String questionSetId, String requestedMode) {
        Room room = requireRoom(code);
        synchronized (room) {
            if (room.players.isEmpty()) throw new IllegalStateException("Cannot start an empty room");
            if (room.phase != Phase.LOBBY) throw new IllegalStateException("Room has already started");
            room.mode = normalizeMode(requestedMode);
            room.prompts = questionSetId == null || questionSetId.isBlank() ? samplePrompts(room.mode) : questionSets.loadPrompts(questionSetId);
            room.questionIndex = 0; intro(room);
        }
    }
    public void submit(String code, String playerId, Answer answer) {
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
            Submission submission = new Submission(answer.optionId(), received);
            if (player.submissions.putIfAbsent(room.questionIndex, submission) != null) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("DUPLICATE", 0, received-room.serverStart)); return;
            }
            long elapsed = Math.max(0, received - room.serverStart);
            boolean correct = room.current().correctOptionId().equals(player.optionMapping.get(answer.optionId()));
            int score = correct ? 500 + (int)Math.floor(500 * (1.0 - (double)elapsed / room.allowedMs)) : 0;
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
    public Snapshot snapshot(String code, String playerId) {
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
            return new Snapshot(room.code, room.phase, room.mode, room.questionIndex,
                    end == 0 ? 0 : Math.max(0, end - System.currentTimeMillis()), options, currentQuestion,
                    player.submissions.containsKey(room.questionIndex), player.score, rank(room, player), standings(room));
        }
    }
    private void intro(Room room) {
        room.phase = Phase.QUESTION_INTRO; room.transitionAt = System.currentTimeMillis() + INTRO_MS;
        for (Player player : room.players.values()) permuteForPlayer(room.current(), player);
        broadcastState(room); room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_INTRO) activate(room); } }, INTRO_MS, TimeUnit.MILLISECONDS);
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
        room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_ACTIVE) reveal(room); } }, room.allowedMs + JITTER_MS, TimeUnit.MILLISECONDS);
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
        room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_REVEAL) leaderboard(room); } }, REVEAL_MS, TimeUnit.MILLISECONDS);
    }
    private void leaderboard(Room room) {
        room.phase = Phase.ROUND_LEADERBOARD; room.transitionAt = System.currentTimeMillis() + BOARD_MS;
        messaging.convertAndSend(topic(room, "leaderboard"), standings(room)); broadcastState(room);
        room.task = clock.schedule(() -> { synchronized (room) {
            if (room.phase != Phase.ROUND_LEADERBOARD) return;
            if (++room.questionIndex >= room.prompts.size()) {
                room.phase = Phase.FINAL_PODIUM; room.transitionAt = 0; persistMatch(room);
                messaging.convertAndSend(topic(room, "results"), standings(room)); broadcastState(room);
            } else intro(room);
        } }, BOARD_MS, TimeUnit.MILLISECONDS);
    }
    private void broadcastState(Room room) {
        Map<String,Object> state=Map.of("phase", room.phase, "questionIndex", room.questionIndex,
                "remainingMs", Math.max(0, room.transitionAt-System.currentTimeMillis()), "mode", room.mode);
        messaging.convertAndSend(topic(room, "state"), state); cache.put("room:" + room.code + ":state", state);
    }
    private void publishRoster(Room room) {
        List<PlayerView> roster=standings(room); messaging.convertAndSend(topic(room, "roster"), roster);
        cache.put("room:" + room.code + ":roster", roster);
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
        pointsByCollege.forEach((name, points) -> {
            College college = colleges.findByName(name).orElseGet(() -> new College(name, null));
            college.addPoints(points); College saved = colleges.save(college);
            matches.save(new MatchHistory(room.code, saved, winner));
        });
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
        List<Prompt> prompts=List.of(); String mode="CLASSIC"; int questionIndex; long serverStart, endsAt, allowedMs, transitionAt;
        ScheduledFuture<?> task; final Map<String,Integer> distribution=new HashMap<>();
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
    private record Submission(String optionId,long receivedAt) {}
    private static final class TopicCounter {
        int answered, correct; long totalSpeed;
        synchronized void add(boolean isCorrect, long speed) { answered++; if(isCorrect) correct++; totalSpeed += speed; }
        synchronized TopicAnalytics view() { return new TopicAnalytics(answered,correct,answered==0?0.0:(double)correct*100/answered,answered==0?0:totalSpeed/answered); }
    }
}
