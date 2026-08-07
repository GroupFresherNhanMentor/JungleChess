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

@Component
public class BotSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BotSessionManager.class);

    private final StompConnectionService connection;
    private final BotEngine botEngine;

    // key = roomId:side
    private final Map<String, BotGameSession> sessions = new ConcurrentHashMap<>();
    // queued when STOMP not yet connected
    private final List<PendingAssignment> pendingQueue = new ArrayList<>();

    public BotSessionManager(StompConnectionService connection, BotEngine botEngine) {
        this.connection = connection;
        this.botEngine = botEngine;
        connection.addOnConnectedCallback(this::processPending);
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
        // Re-join all existing (non-ended) sessions after reconnect
        sessions.values().stream()
                .filter(s -> !s.isEnded())
                .forEach(s -> {
                    // Re-subscribe handled by StompConnectionService (subscriptions map)
                    // Re-send bot-join so server re-registers the new session
                    connection.send("/app/room/" + s.getRoomId() + "/bot-join",
                            new BotJoinRequestDto(s.getSide(), "MEDIUM"));
                });

        List<PendingAssignment> toProcess = new ArrayList<>(pendingQueue);
        pendingQueue.clear();
        toProcess.forEach(p -> doAssign(p.roomId(), p.side(), p.difficulty()));
    }

    private void doAssign(String roomId, String side, String difficulty) {
        BotGameSession session = new BotGameSession(roomId, side, difficulty, botEngine, connection);
        sessions.put(roomId + ":" + side, session);

        connection.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof Map<?, ?> map) {
                    session.handleEvent(map);
                    if (session.isEnded()) {
                        sessions.remove(roomId + ":" + side);
                        log.info("Removed ended session for room {} side {}", roomId, side);
                    }
                }
            }
        });

        connection.send("/app/room/" + roomId + "/bot-join", new BotJoinRequestDto(side, difficulty));
        log.info("Bot assigned: room={} side={} difficulty={}", roomId, side, difficulty);
    }

    public int activeSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.isEnded()).count();
    }

    private record PendingAssignment(String roomId, String side, String difficulty) {}
}
