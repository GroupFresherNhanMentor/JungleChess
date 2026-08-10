package fpt.qn.junglechess.botworker.stomp;

import fpt.qn.junglechess.botworker.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StompConnectionService {

    private static final Logger log = LoggerFactory.getLogger(StompConnectionService.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final WebSocketStompClient stompClient;
    private final BotProperties props;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    // topic → handler, preserved across reconnects
    private final Map<String, StompFrameHandler> subscriptions = new ConcurrentHashMap<>();
    private final List<Runnable> onConnectedCallbacks = new ArrayList<>();

    private volatile StompSession session;

    public StompConnectionService(WebSocketStompClient stompClient, BotProperties props) {
        this.stompClient = stompClient;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        doConnect();
    }

    public void addOnConnectedCallback(Runnable callback) {
        onConnectedCallbacks.add(callback);
    }

    private void doConnect() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        String token = props.getBotToken();
        if (token != null && !token.isBlank()) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        stompClient.connectAsync(props.getServerUrl(), handshakeHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                        session = s;
                        reconnectAttempts.set(0);
                        log.info("STOMP connected: {}", s.getSessionId());
                        // Re-subscribe all known topics
                        subscriptions.forEach((topic, handler) -> s.subscribe(topic, handler));
                        // Notify callbacks (e.g. BotSessionManager re-sends bot-join for pending)
                        onConnectedCallbacks.forEach(Runnable::run);
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable ex) {
                        log.error("STOMP transport error: {}", ex.getMessage());
                        scheduleReconnect();
                    }
                })
                .exceptionally(ex -> {
                    log.error("STOMP connection failed to {}: {}", props.getServerUrl(), ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Exhausted reconnect attempts. Giving up.");
            return;
        }
        long delay = Math.min((long) Math.pow(2, attempt), 60L);
        log.info("Scheduling reconnect attempt {}/{} in {}s", attempt, MAX_RECONNECT_ATTEMPTS, delay);
        reconnectScheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    public void subscribe(String topic, StompFrameHandler handler) {
        subscriptions.put(topic, handler);
        if (isConnected()) {
            session.subscribe(topic, handler);
        }
    }

    public void send(String destination, Object payload) {
        if (isConnected()) {
            session.send(destination, payload);
        } else {
            log.warn("Cannot send to {} — STOMP not connected", destination);
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }
}
