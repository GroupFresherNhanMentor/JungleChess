package fpt.qn.junglechess.botworker.session;

import fpt.qn.junglechess.botworker.dto.BotJoinRequestDto;
import fpt.qn.junglechess.botworker.stomp.StompConnectionService;
import fpt.qn.junglechess.game.bot.BotEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BotSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BotSessionManager.class);

    private final StompConnectionService connection;
    private final BotEngine botEngine;

    // key = roomId:side
    private final Map<String, BotGameSession> sessions = new ConcurrentHashMap<>();
    // key = "/topic/room/{roomId}" → all bot sessions listening on that topic (EVE has 2)
    private final Map<String, CopyOnWriteArrayList<BotGameSession>> topicSessions = new ConcurrentHashMap<>();
    // queued when STOMP not yet connected
    private final List<PendingAssignment> pendingQueue = new ArrayList<>();

    public BotSessionManager(StompConnectionService connection, BotEngine botEngine) {
        this.connection = connection;
        this.botEngine = botEngine;
        connection.addOnConnectedCallback(this::processPending);
        connection.setOnAssignCallback(this::handleAssignMessage);
    }

    /** Called by the WebSocket assign callback when the backend pushes an assignment. */
    private synchronized void handleAssignMessage(Map<?, ?> map) {
        String roomId = (String) map.get("roomId");
        String side = (String) map.get("side");
        String difficulty = map.get("difficulty") instanceof String d ? d : "MEDIUM";
        if (roomId != null && side != null) {
            assign(roomId, side, difficulty);
        } else {
            log.warn("Received malformed assign message: {}", map);
        }
    }

    public synchronized void assign(String roomId, String side, String difficulty) {
        String key = roomId + ":" + side;
        if (sessions.containsKey(key)) {
            log.warn("Bot already assigned to room {} as {}", roomId, side);
            return;
        }
        if (!connection.isConnected()) {
            log.info("STOMP not ready, queuing assignment for room {} side {}", roomId, side);
            pendingQueue.add(new PendingAssignment(roomId, side, difficulty));
            return;
        }
        doAssign(roomId, side, difficulty);
    }

    private synchronized void processPending() {
        sessions.values().stream()
                .filter(s -> !s.isEnded())
                .forEach(s -> connection.send("/app/room/" + s.getRoomId() + "/bot-join",
                        new BotJoinRequestDto(s.getSide(), s.getDifficulty())));

        List<PendingAssignment> toProcess = new ArrayList<>(pendingQueue);
        pendingQueue.clear();
        toProcess.forEach(p -> doAssign(p.roomId(), p.side(), p.difficulty()));
    }

    private void doAssign(String roomId, String side, String difficulty) {
        BotGameSession session = new BotGameSession(roomId, side, difficulty, botEngine, connection);
        sessions.put(roomId + ":" + side, session);

        String topic = "/topic/room/" + roomId;
        CopyOnWriteArrayList<BotGameSession> roomSessions =
                topicSessions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        roomSessions.add(session);

        if (roomSessions.size() == 1) {
            // First bot for this room — register one shared STOMP subscription.
            // A second bot (EVE PLAYER_2) reuses this same subscription via topicSessions.
            connection.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return Map.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (!(payload instanceof Map<?, ?> map)) return;
                    CopyOnWriteArrayList<BotGameSession> active = topicSessions.get(topic);
                    if (active == null) return;
                    active.removeIf(s -> {
                        s.handleEvent(map);
                        if (s.isEnded()) {
                            sessions.remove(s.getRoomId() + ":" + s.getSide());
                            log.info("Removed ended session room={} side={}", s.getRoomId(), s.getSide());
                            return true;
                        }
                        return false;
                    });
                    if (active.isEmpty()) topicSessions.remove(topic);
                }
            });
        }

        connection.send("/app/room/" + roomId + "/bot-join", new BotJoinRequestDto(side, difficulty));
        log.info("Bot assigned: room={} side={} difficulty={}", roomId, side, difficulty);
    }

    public int activeSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.isEnded()).count();
    }

    private record PendingAssignment(String roomId, String side, String difficulty) {}
}
