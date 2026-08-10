package fpt.qn.junglechess.botworker.session;

import fpt.qn.junglechess.botworker.stomp.StompConnectionService;
import fpt.qn.junglechess.botworker.dto.MoveRequestDto;
import fpt.qn.junglechess.game.bot.BotDifficulty;
import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BotGameSession {

    private static final Logger log = LoggerFactory.getLogger(BotGameSession.class);
    private static final long MIN_MOVE_MS = 1_200;

    private final String roomId;
    private final String side;
    private final BotDifficulty difficulty;
    private final BotEngine botEngine;
    private final StompConnectionService connection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Per-game context (TT + position history) owned by this game session. */
    private final fpt.qn.junglechess.game.bot.BotContext botContext = new fpt.qn.junglechess.game.bot.BotContext();

    private volatile boolean ended = false;

    public BotGameSession(String roomId, String side, String difficulty,
                          BotEngine botEngine, StompConnectionService connection) {
        this.roomId = roomId;
        this.side = side;
        this.difficulty = BotDifficulty.from(difficulty);
        this.botEngine = botEngine;
        this.connection = connection;
    }

    public void handleEvent(Map<?, ?> map) {
        if (ended) return;
        // Backend sends "type" field (from @JsonTypeInfo property = "type")
        String type = (String) map.get("type");

        if ("STATE_UPDATED".equals(type) || map.containsKey("board")) {
            handleStateUpdate(map);
        } else if ("GAME_RESULT".equals(type) || map.containsKey("winner")) {
            ended = true;
            log.info("[Room {}] Game ended — shutting down session", roomId);
            executor.shutdown();
        }
    }

    private void handleStateUpdate(Map<?, ?> map) {
        String status = (String) map.get("status");
        String currentTurn = (String) map.get("currentTurn");

        if ("ENDED".equals(status)) {
            ended = true;
            executor.shutdown();
            return;
        }

        if (!"PLAYING".equals(status)) return;

        // Record incoming board state hash for repetition tracking
        String[][] boardArray = parseBoard(map);
        Board board = boardFromArray(boardArray);
        Side turnSide = currentTurn != null ? Side.valueOf(currentTurn.toUpperCase()) : Side.PLAYER_1;
        long posKey = board.getZobristHash();
        if (turnSide == Side.PLAYER_2) {
            posKey ^= fpt.qn.junglechess.game.model.ZobristTable.SIDE_TO_MOVE_KEY;
        }
        botContext.recordPosition(posKey);

        if (side.equalsIgnoreCase(currentTurn)) {
            log.debug("[Room {}] Bot's turn ({}), computing move...", roomId, side);
            executor.submit(() -> computeAndSendMove(board));
        }
    }

    private String[][] parseBoard(Map<?, ?> map) {
        String[][] result = new String[Board.ROWS][Board.COLS];
        Object raw = map.get("board");
        if (!(raw instanceof Iterable<?> rows)) return result;
        int r = 0;
        for (Object rowObj : rows) {
            if (rowObj instanceof Iterable<?> cols && r < Board.ROWS) {
                int c = 0;
                for (Object cell : cols) {
                    if (c < Board.COLS && cell != null) {
                        String code = cell.toString();
                        if (!code.isBlank() && !"null".equalsIgnoreCase(code)) {
                            result[r][c] = code;
                        }
                    }
                    c++;
                }
            }
            r++;
        }
        return result;
    }

    private void computeAndSendMove(Board board) {
        long startMs = System.currentTimeMillis();
        try {
            Side botSide = Side.valueOf(side.toUpperCase());
            Move best = botEngine.nextMove(board, botSide, difficulty.getSearchDepth(), 2500, botContext);
            if (best != null) {
                long elapsed = System.currentTimeMillis() - startMs;
                long remaining = MIN_MOVE_MS - elapsed;
                if (remaining > 0) Thread.sleep(remaining);
                MoveRequestDto req = new MoveRequestDto(
                        new int[]{best.from().row(), best.from().col()},
                        new int[]{best.to().row(), best.to().col()});
                connection.send("/app/room/" + roomId + "/move", req);
                log.info("[Room {}] Bot move sent: ({},{}) → ({},{})",
                        roomId, best.from().row(), best.from().col(),
                        best.to().row(), best.to().col());
            } else {
                log.warn("[Room {}] No legal move found!", roomId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[Room {}] Error computing move: {}", roomId, e.getMessage(), e);
        }
    }

    private Board boardFromArray(String[][] arr) {
        Board board = new Board();
        if (arr == null) return board;
        for (int r = 0; r < arr.length && r < Board.ROWS; r++) {
            if (arr[r] == null) continue;
            for (int c = 0; c < arr[r].length && c < Board.COLS; c++) {
                String code = arr[r][c];
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

    public boolean isEnded() {
        return ended;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSide() {
        return side;
    }

    public String getDifficulty() {
        return difficulty.name();
    }
}
