package com.arenax.game;

import com.arenax.game.GameTypes.*;
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
    public RoomEngine(SimpMessagingTemplate messaging) { this.messaging = messaging; }
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
    public void start(String code) {
        Room room = requireRoom(code);
        synchronized (room) {
            if (room.players.isEmpty()) throw new IllegalStateException("Cannot start an empty room");
            if (room.phase != Phase.LOBBY) throw new IllegalStateException("Room has already started");
            room.prompts = samplePrompts(); room.questionIndex = 0; intro(room);
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
            Submission submission = new Submission(answer.optionId(), received);
            if (player.submissions.putIfAbsent(room.questionIndex, submission) != null) {
                sendPlayer(playerId, "answer-result", new SubmissionResult("DUPLICATE", 0, received-room.serverStart)); return;
            }
            long elapsed = Math.max(0, received - room.serverStart);
            boolean correct = room.current().correctOptionId().equals(player.optionMapping.get(answer.optionId()));
            int score = correct ? 500 + (int)Math.floor(500 * (1.0 - Math.min(1.0, (double)elapsed / room.allowedMs))): 0;
            player.score += score; player.answersCorrect += correct ? 1 : 0; player.responseTimes.add(elapsed);
            room.distribution.merge(player.optionMapping.get(answer.optionId()), 1, Integer::sum);
            sendPlayer(playerId, "answer-result", new SubmissionResult("ACCEPTED", score, elapsed));
            messaging.convertAndSend(topic(room, "progress"), Map.of("submitted", countSubmitted(room), "total", room.players.size()));
            if (countSubmitted(room) == room.players.size()) reveal(room);
        }
    }
    public Snapshot snapshot(String code, String playerId) {
        Room room = requireRoom(code);
        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null) throw new IllegalArgumentException("Player is not in room");
            List<Option> options = room.phase == Phase.QUESTION_ACTIVE || room.phase == Phase.QUESTION_INTRO ?
                    shuffled(room.current(), player).stream().map(o -> new Option(o.id(), o.text())).toList() : List.of();
            long end = room.phase == Phase.QUESTION_ACTIVE ? room.endsAt : room.transitionAt;
            return new Snapshot(room.code, room.phase, room.questionIndex,
                    end == 0 ? 0 : Math.max(0, end - System.currentTimeMillis()), options,
                    player.submissions.containsKey(room.questionIndex), player.score, rank(room, player), standings(room));
        }
    }
    private void intro(Room room) {
        room.phase = Phase.QUESTION_INTRO; room.transitionAt = System.currentTimeMillis() + INTRO_MS;
        broadcastState(room); room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_INTRO) activate(room); } }, INTRO_MS, TimeUnit.MILLISECONDS);
    }
    private void activate(Room room) {
        room.phase = Phase.QUESTION_ACTIVE; room.serverStart = System.currentTimeMillis();
        room.allowedMs = Math.max(20, Math.min(45, room.current().durationSeconds())) * 1000L;
        room.endsAt = room.serverStart + room.allowedMs; room.transitionAt = room.endsAt;
        room.distribution.clear(); broadcastState(room);
        PublicQuestion publicQuestion = new PublicQuestion(room.questionIndex, room.current().id(), room.current().prompt(),
                room.current().options(), room.current().topic(), room.serverStart, room.endsAt,
                room.current().imageUrl(), room.current().tableData());
        messaging.convertAndSend(topic(room, "question"), publicQuestion);
        for (Player player : room.players.values()) {
            List<Option> shuffle = shuffled(room.current(), player);
            player.optionMapping.clear(); shuffle.forEach(o -> player.optionMapping.put(o.id(), o.id()));
            sendPlayer(player.identity.playerId(), "question", new PublicQuestion(room.questionIndex, room.current().id(), room.current().prompt(),
                    shuffle, room.current().topic(), room.serverStart, room.endsAt, room.current().imageUrl(), room.current().tableData()));
        }
        room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_ACTIVE) reveal(room); } }, room.allowedMs + JITTER_MS, TimeUnit.MILLISECONDS);
    }
    private List<Option> shuffled(Prompt prompt, Player player) {
        List<Option> options = new ArrayList<>(prompt.options());
        long seed = Objects.hash(player.identity.playerId(), prompt.id()); Collections.shuffle(options, new Random(seed));
        return options;
    }
    private void reveal(Room room) {
        if (room.task != null) room.task.cancel(false);
        room.phase = Phase.QUESTION_REVEAL; room.transitionAt = System.currentTimeMillis() + REVEAL_MS;
        messaging.convertAndSend(topic(room, "reveal"), Map.of("questionIndex", room.questionIndex,
                "correctOptionId", room.current().correctOptionId(), "distribution", Map.copyOf(room.distribution), "standings", standings(room)));
        broadcastState(room);
        room.task = clock.schedule(() -> { synchronized (room) { if (room.phase == Phase.QUESTION_REVEAL) leaderboard(room); } }, REVEAL_MS, TimeUnit.MILLISECONDS);
    }
    private void leaderboard(Room room) {
        room.phase = Phase.ROUND_LEADERBOARD; room.transitionAt = System.currentTimeMillis() + BOARD_MS;
        messaging.convertAndSend(topic(room, "leaderboard"), standings(room)); broadcastState(room);
        room.task = clock.schedule(() -> { synchronized (room) {
            if (room.phase != Phase.ROUND_LEADERBOARD) return;
            if (++room.questionIndex >= room.prompts.size()) {
                room.phase = Phase.FINAL_PODIUM; room.transitionAt = 0; messaging.convertAndSend(topic(room, "results"), standings(room)); broadcastState(room);
            } else intro(room);
        } }, BOARD_MS, TimeUnit.MILLISECONDS);
    }
    private void broadcastState(Room room) { messaging.convertAndSend(topic(room, "state"), Map.of("phase", room.phase, "questionIndex", room.questionIndex, "remainingMs", Math.max(0, room.transitionAt-System.currentTimeMillis()))); }
    private void publishRoster(Room room) { messaging.convertAndSend(topic(room, "roster"), standings(room)); }
    private List<PlayerView> standings(Room room) { List<Player> sorted = new ArrayList<>(room.players.values()); sorted.sort(Comparator.comparingInt((Player p)->p.score).reversed().thenComparing(p->p.identity.name())); List<PlayerView> views=new ArrayList<>(); for(int i=0;i<sorted.size();i++) views.add(new PlayerView(sorted.get(i).identity.playerId(),sorted.get(i).identity.name(),sorted.get(i).identity.college(),sorted.get(i).score,i+1,sorted.get(i).submissions.containsKey(room.questionIndex))); return views; }
    private PlayerView view(Room room, Player player) { return standings(room).stream().filter(v->v.playerId().equals(player.identity.playerId())).findFirst().orElseThrow(); }
    private int rank(Room room, Player player) { return view(room, player).rank(); }
    private int countSubmitted(Room room) { return (int)room.players.values().stream().filter(p->p.submissions.containsKey(room.questionIndex)).count(); }
    private String topic(Room room, String event) { return "/topic/room/"+room.code+"/"+event; }
    private void sendPlayer(String playerId, String queue, Object payload) { messaging.convertAndSendToUser(playerId, "/queue/"+queue, payload); }
    private Room requireRoom(String code) { Room room=rooms.get(code.toUpperCase(Locale.ROOT)); if(room==null) throw new IllegalArgumentException("Unknown room"); return room; }
    private List<Prompt> samplePrompts() { return List.of(
        new Prompt("apt-1","A train travels 120 km in 2 hours. What is its average speed?",List.of(new Option("A","40 km/h"),new Option("B","50 km/h"),new Option("C","60 km/h"),new Option("D","80 km/h")),"C","Quant",20,null,List.of()),
        new Prompt("apt-2","Choose the word closest in meaning to concise.",List.of(new Option("A","Brief"),new Option("B","Vague"),new Option("C","Loud"),new Option("D","Formal")),"A","Verbal",25,null,List.of()),
        new Prompt("apt-3","Find the next number: 2, 6, 12, 20, ?",List.of(new Option("A","24"),new Option("B","28"),new Option("C","30"),new Option("D","32")),"B","Logical",30,null,List.of())); }

    private static final class Room {
        final String code; final Map<String,Player> players=new ConcurrentHashMap<>(); Phase phase=Phase.LOBBY;
        List<Prompt> prompts=List.of(); int questionIndex; long serverStart, endsAt, allowedMs, transitionAt;
        ScheduledFuture<?> task; final Map<String,Integer> distribution=new HashMap<>();
        Room(String code) { this.code=code; }
        Prompt current() { return prompts.get(questionIndex); }
    }
    private static final class Player {
        final SessionRegistry.Identity identity; int score, answersCorrect; final List<Long> responseTimes=new ArrayList<>();
        final Map<Integer,Submission> submissions=new ConcurrentHashMap<>(); final Map<String,String> optionMapping=new ConcurrentHashMap<>();
        Player(SessionRegistry.Identity identity) { this.identity=identity; }
    }
    private record Submission(String optionId,long receivedAt) {}
}
