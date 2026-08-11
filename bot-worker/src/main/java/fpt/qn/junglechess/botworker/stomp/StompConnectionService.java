package fpt.qn.junglechess.botworker.stomp;

import fpt.qn.junglechess.botworker.auth.BotAuthClient;
import fpt.qn.junglechess.botworker.auth.BotAuthClient.BotTokens;
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class StompConnectionService {

    private static final Logger log = LoggerFactory.getLogger(StompConnectionService.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final WebSocketStompClient stompClient;
    private final BotProperties props;
    private final BotAuthClient authClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private final Map<String, StompFrameHandler> subscriptions = new ConcurrentHashMap<>();
    private final List<Runnable> onConnectedCallbacks = new ArrayList<>();
    private Consumer<Map<?, ?>> onAssignCallback;

    private volatile StompSession session;
    private volatile String currentAccessToken;
    private volatile String currentRefreshToken;

    public StompConnectionService(WebSocketStompClient stompClient, BotProperties props, BotAuthClient authClient) {
        this.stompClient = stompClient;
        this.props = props;
        this.authClient = authClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        loginAndConnect();
    }

    public void addOnConnectedCallback(Runnable callback) {
        onConnectedCallbacks.add(callback);
    }

    public void setOnAssignCallback(Consumer<Map<?, ?>> callback) {
        this.onAssignCallback = callback;
    }

    private void loginAndConnect() {
        try {
            authClient.register();
            log.info("Logging in as bot user '{}'", props.getBotUsername());
            BotTokens tokens = authClient.login();
            currentAccessToken = tokens.accessToken();
            currentRefreshToken = tokens.refreshToken();
            doConnect();
        } catch (Exception e) {
            log.error("Bot login failed: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void refreshAndConnect() {
        if (currentRefreshToken != null) {
            try {
                log.info("Refreshing bot access token");
                BotTokens tokens = authClient.refresh(currentRefreshToken);
                currentAccessToken = tokens.accessToken();
                currentRefreshToken = tokens.refreshToken();
                doConnect();
                return;
            } catch (Exception e) {
                log.warn("Token refresh failed, falling back to login: {}", e.getMessage());
            }
        }
        loginAndConnect();
    }

    private void doConnect() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        if (currentAccessToken != null && !currentAccessToken.isBlank()) {
            connectHeaders.add("Authorization", "Bearer " + currentAccessToken);
        }

        stompClient.connectAsync(props.getServerUrl(), handshakeHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                        session = s;
                        reconnectAttempts.set(0);
                        log.info("STOMP connected: {}", s.getSessionId());

                        // Re-subscribe all room topics
                        subscriptions.forEach((topic, handler) -> s.subscribe(topic, handler));

                        // Subscribe to bot invite queue (server pushes room invitations here)
                        s.subscribe("/user/queue/bot-invite", new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) { return Map.class; }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                if (payload instanceof Map<?, ?> map && onAssignCallback != null) {
                                    onAssignCallback.accept(map);
                                }
                            }
                        });

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
        reconnectScheduler.schedule(this::refreshAndConnect, delay, TimeUnit.SECONDS);
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

    @jakarta.annotation.PreDestroy
    public void destroy() {
        reconnectScheduler.shutdownNow();
    }
}
