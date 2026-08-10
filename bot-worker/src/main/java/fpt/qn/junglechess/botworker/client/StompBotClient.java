package fpt.qn.junglechess.botworker.client;

import fpt.qn.junglechess.botworker.dto.BotJoinRequestDto;
import fpt.qn.junglechess.botworker.dto.GameResultEventDto;
import fpt.qn.junglechess.botworker.dto.MoveRequestDto;
import fpt.qn.junglechess.botworker.dto.StateUpdatedEventDto;
import fpt.qn.junglechess.game.bot.BotDifficulty;
import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StompBotClient implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StompBotClient.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private final WebSocketStompClient stompClient;
    private final BotEngine botEngine;
    private final ExecutorService moveExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @Value("${bot.room-id}")
    private String roomId;

    @Value("${bot.server-url}")
    private String serverUrl;

    @Value("${bot.side}")
    private String side;

    @Value("${bot.difficulty}")
    private String difficulty;

    @Value("${bot.bot-token:}")
    private String botToken;

    private volatile StompSession stompSession;

    public StompBotClient(WebSocketStompClient stompClient, BotEngine botEngine) {
        this.stompClient = stompClient;
        this.botEngine = botEngine;
    }

    @Override
    public void run(String... args) {
        log.info("Starting STOMP Bot Worker container for roomId={}, side={}, difficulty={}", roomId, side, difficulty);
        if (botToken == null || botToken.isBlank()) {
            log.warn("BOT_TOKEN is blank. STOMP connection will proceed unauthenticated.");
        }
        connectAndJoin();
    }

    public void connectAndJoin() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        if (botToken != null && !botToken.isBlank()) {
            connectHeaders.add("Authorization", "Bearer " + botToken);
        }

        stompClient.connectAsync(serverUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                reconnectAttempts.set(0);
                log.info("STOMP session connected successfully: {}", session.getSessionId());

                // Subscribe to room updates
                session.subscribe("/topic/room/" + roomId, new RoomStompFrameHandler());

                // Send bot-join message
                BotJoinRequestDto joinReq = new BotJoinRequestDto(side, difficulty);
                session.send("/app/room/" + roomId + "/bot-join", joinReq);
                log.info("Sent bot-join to /app/room/{}/bot-join", roomId);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.error("STOMP Session exception: {}", exception.getMessage(), exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("STOMP Transport error: {}", exception.getMessage(), exception);
                scheduleReconnect();
            }
        }).exceptionally(ex -> {
            log.error("Initial STOMP connection failed to {}: {}", serverUrl, ex.getMessage(), ex);
            scheduleReconnect();
            return null;
        });
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Exhausted maximum reconnect attempts ({}). Shutting down bot container.", MAX_RECONNECT_ATTEMPTS);
            shutdown();
            return;
        }

        long delaySeconds = (long) Math.pow(2, attempt); // 2s, 4s, 8s, 16s, 32s
        log.info("Scheduling reconnect attempt {}/{} in {} seconds...", attempt, MAX_RECONNECT_ATTEMPTS, delaySeconds);
        reconnectScheduler.schedule(this::connectAndJoin, delaySeconds, TimeUnit.SECONDS);
    }

    private class RoomStompFrameHandler implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                if (payload instanceof Map<?, ?> map) {
                    String eventType = (String) map.get("eventType");
                    if ("STATE_UPDATED".equals(eventType) || map.containsKey("board")) {
                        StateUpdatedEventDto dto = parseStateUpdatedDto(map);
                        handleStateUpdate(dto);
                    } else if ("GAME_RESULT".equals(eventType) || map.containsKey("winner")) {
                        GameResultEventDto dto = parseGameResultDto(map);
                        handleGameResult(dto);
                    }
                }
            } catch (Exception e) {
                log.error("Error handling STOMP frame payload: {}", e.getMessage(), e);
            }
        }
    }

    private StateUpdatedEventDto parseStateUpdatedDto(Map<?, ?> map) {
        String roomIdVal = (String) map.get("roomId");
        String currentTurnVal = (String) map.get("currentTurn");
        String statusVal = (String) map.get("status");
        Number moveNumberVal = (Number) map.get("moveNumber");
        int moveNumber = moveNumberVal != null ? moveNumberVal.intValue() : 0;

        String[][] boardState = null;
        Object rawBoard = map.get("board");
        if (rawBoard instanceof Iterable<?> rows) {
            boardState = new String[Board.ROWS][Board.COLS];
            int r = 0;
            for (Object rowObj : rows) {
                if (rowObj instanceof Iterable<?> cols && r < Board.ROWS) {
                    int c = 0;
                    for (Object cell : cols) {
                        if (c < Board.COLS && cell != null) {
                            String code = cell.toString();
                            if (!code.isBlank() && !"null".equalsIgnoreCase(code)) {
                                boardState[r][c] = code;
                            }
                        }
                        c++;
                    }
                }
                r++;
            }
        }

        return new StateUpdatedEventDto(roomIdVal, boardState, currentTurnVal, statusVal, moveNumber);
    }

    private GameResultEventDto parseGameResultDto(Map<?, ?> map) {
        String roomIdVal = (String) map.get("roomId");
        String winnerVal = (String) map.get("winner");
        String reasonVal = (String) map.get("reason");
        return new GameResultEventDto(roomIdVal, winnerVal, reasonVal);
    }

    private void handleStateUpdate(StateUpdatedEventDto dto) {
        log.debug("State update received for room {}: currentTurn={}, status={}", roomId, dto.currentTurn(), dto.status());

        if ("ENDED".equals(dto.status())) {
            log.info("Game has ended for room {}. Bot shutting down.", roomId);
            shutdown();
            return;
        }

        if (side.equalsIgnoreCase(dto.currentTurn())) {
            log.info("It is bot's turn ({})! Computing move off-thread...", side);
            CompletableFuture.runAsync(() -> computeAndSendMove(dto.board()), moveExecutor);
        }
    }

    private void computeAndSendMove(String[][] boardArray) {
        try {
            Board board = parseBoard(boardArray);
            BotDifficulty botDiff = BotDifficulty.from(difficulty);
            Side botSide = Side.valueOf(side.toUpperCase());

            Move bestMove = botEngine.nextMove(board, botSide, botDiff.getSearchDepth(), 2500);

            if (bestMove != null && stompSession != null && stompSession.isConnected()) {
                MoveRequestDto moveReq = new MoveRequestDto(
                        new int[]{bestMove.from().row(), bestMove.from().col()},
                        new int[]{bestMove.to().row(), bestMove.to().col()}
                );
                stompSession.send("/app/room/" + roomId + "/move", moveReq);
                log.info("Bot executed move: from=({},{}) to=({},{})",
                        bestMove.from().row(), bestMove.from().col(),
                        bestMove.to().row(), bestMove.to().col());
            } else {
                log.warn("No legal move found or STOMP session disconnected!");
            }
        } catch (Exception e) {
            log.error("Error computing or sending move: {}", e.getMessage(), e);
        }
    }

    private void handleGameResult(GameResultEventDto dto) {
        log.info("Game result for room {}: Winner={}, Reason={}. Bot shutting down.", roomId, dto.winner(), dto.reason());
        shutdown();
    }

    private Board parseBoard(String[][] boardState) {
        Board board = new Board();
        if (boardState == null) {
            return board;
        }
        for (int r = 0; r < boardState.length && r < Board.ROWS; r++) {
            if (boardState[r] == null) continue;
            for (int c = 0; c < boardState[r].length && c < Board.COLS; c++) {
                String code = boardState[r][c];
                if (code != null && !code.isBlank() && !"null".equalsIgnoreCase(code)) {
                    int idx = code.lastIndexOf('_');
                    if (idx > 0) {
                        Side s = Side.valueOf(code.substring(0, idx));
                        PieceType t = PieceType.valueOf(code.substring(idx + 1));
                        board.setPiece(r, c, new Piece(s, t));
                    }
                }
            }
        }
        return board;
    }

    private void shutdown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        moveExecutor.shutdown();
        reconnectScheduler.shutdown();
        System.exit(0);
    }
}

