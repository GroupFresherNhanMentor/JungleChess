package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.common.util.UuidV7;
import fpt.qn.junglechess.game.bot.BotDifficulty;
import fpt.qn.junglechess.game.bot.config.BotConfigService;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.PlayerSide;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.BoardInitializer;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import fpt.qn.junglechess.room.dto.event.GameResultEvent;
import fpt.qn.junglechess.room.dto.event.PlayersUpdatedEvent;
import fpt.qn.junglechess.room.dto.event.RoomEvent;
import fpt.qn.junglechess.room.dto.event.StateUpdatedEvent;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.request.MoveRequest;
import fpt.qn.junglechess.room.dto.response.CreateRoomResponse;
import fpt.qn.junglechess.room.dto.response.JoinRoomResponse;
import fpt.qn.junglechess.room.dto.response.MoveAckResponse;
import fpt.qn.junglechess.room.exception.ActionNotAllowedException;
import fpt.qn.junglechess.room.exception.InvalidMoveException;
import fpt.qn.junglechess.room.exception.NotYourTurnException;
import fpt.qn.junglechess.room.exception.RoomFullException;
import fpt.qn.junglechess.room.exception.RoomNotFoundException;
import fpt.qn.junglechess.room.model.GameMode;
import fpt.qn.junglechess.room.model.MoveRecord;
import fpt.qn.junglechess.room.model.PlayerInfo;
import fpt.qn.junglechess.room.model.RoomState;
import fpt.qn.junglechess.room.model.RoomStatus;
import fpt.qn.junglechess.room.model.SpectatorInfo;
import fpt.qn.junglechess.room.repository.RoomStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomStateRepository roomRepo;
    private final RoomEventBus eventBus;
    private final LobbyService lobbyService;
    private final GameRuleEngine ruleEngine;
    private final SessionRegistry sessionRegistry;
    private final BotConfigService botConfigService;

    // ── Create ────────────────────────────────────────────────────────────────

    public Mono<CreateRoomResponse> createRoom(CreateRoomRequest req, String sessionId, String userId) {
        String roomId = "room-" + UuidV7.generate().toString().substring(0, 8);

        boolean allowSpectator = req.getMode() == GameMode.EVE || req.isAllowSpectator();
        boolean allowBet       = req.getMode() == GameMode.EVE || (allowSpectator && req.isAllowBet());

        Instant now = Instant.now();

        PlayerInfo player1 = PlayerInfo.builder()
                .sessionId(sessionId)
                .side(PlayerSide.PLAYER_1.name())
                .isBot(false)
                .userId(userId)
                .build();

        RoomState state = RoomState.builder()
                .roomId(roomId)
                .mode(req.getMode())
                .status(RoomStatus.WAITING)
                .allowSpectator(allowSpectator)
                .allowBet(allowBet)
                .board(BoardInitializer.standard().toBoardStateArray())
                .currentTurn(PlayerSide.PLAYER_1.name())
                .moveNumber(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        state.getPlayers().add(player1);

        boolean isBotMode = req.getMode() == GameMode.PVE || req.getMode() == GameMode.EVE;
        Mono<Integer> depthMono = isBotMode
                ? botConfigService.resolveDepth(BotDifficulty.from(req.getBotDifficulty()))
                : Mono.just(0);

        return depthMono.flatMap(depth -> {
            state.setBotSearchDepth(depth);
            return roomRepo.save(state)
                    .then(roomRepo.saveSessionMapping(sessionId, roomId))
                    .then(refreshLobby())
                    .thenReturn(CreateRoomResponse.builder()
                            .roomId(roomId)
                            .mode(req.getMode().name())
                            .status(RoomStatus.WAITING.name())
                            .yourSide(PlayerSide.PLAYER_1.name())
                            .allowSpectator(allowSpectator)
                            .allowBet(allowBet)
                            .build());
        });
    }

    // ── Join (as player) ──────────────────────────────────────────────────────

    public Mono<JoinRoomResponse> joinRoom(String roomId, String sessionId, boolean isBot, String userId) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.error(new RoomNotFoundException(roomId)))
                .flatMap(state -> {
                    if (state.getStatus() != RoomStatus.WAITING) {
                        return Mono.error(new ActionNotAllowedException("game already started or ended"));
                    }
                    if (state.getPlayers().size() >= 2) {
                        return Mono.error(new RoomFullException());
                    }

                    PlayerInfo player2 = PlayerInfo.builder()
                            .sessionId(sessionId)
                            .side(PlayerSide.PLAYER_2.name())
                            .isBot(isBot)
                            .userId(userId)
                            .build();
                    state.getPlayers().add(player2);
                    state.setStatus(RoomStatus.PLAYING);
                    state.setUpdatedAt(Instant.now());

                    return roomRepo.save(state)
                            .then(roomRepo.saveSessionMapping(sessionId, roomId))
                            .then(refreshLobby())
                            .doOnSuccess(v -> eventBus.emit(roomId, new PlayersUpdatedEvent(
                                    roomId, state.getPlayers(), state.getSpectators(),
                                    RoomStatus.PLAYING.name())))
                            .thenReturn(JoinRoomResponse.builder()
                                    .yourSide(PlayerSide.PLAYER_2.name())
                                    .status(RoomStatus.PLAYING.name())
                                    .build());
                });
    }

    // ── Watch (as spectator) ──────────────────────────────────────────────────

    public Mono<Void> watchRoom(String roomId, String sessionId, String userId) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.error(new RoomNotFoundException(roomId)))
                .flatMap(state -> {
                    if (!state.isAllowSpectator()) {
                        return Mono.error(new ActionNotAllowedException("spectators not allowed in this room"));
                    }
                    if (state.getStatus() == RoomStatus.ENDED) {
                        return Mono.error(new ActionNotAllowedException("game has ended"));
                    }

                    SpectatorInfo spectator = SpectatorInfo.builder()
                            .sessionId(sessionId)
                            .userId(userId)
                            .build();
                    state.getSpectators().add(spectator);
                    state.setUpdatedAt(Instant.now());

                    return roomRepo.save(state)
                            .then(roomRepo.saveSessionMapping(sessionId, roomId))
                            .then(refreshLobby())
                            .doOnSuccess(v -> {
                                eventBus.emit(roomId, new PlayersUpdatedEvent(
                                        roomId, state.getPlayers(), state.getSpectators(),
                                        state.getStatus().name()));
                                eventBus.emitToSession(sessionId, new StateUpdatedEvent(
                                        roomId, state.getBoard(), state.getCurrentTurn(),
                                        state.getHistory().isEmpty() ? null
                                                : state.getHistory().get(state.getHistory().size() - 1),
                                        state.getStatus().name(), state.getMoveNumber()));
                            });
                });
    }

    // ── Subscribe (event stream) ──────────────────────────────────────────────

    public Flux<RoomEvent> subscribeRoom(String roomId, String sessionId) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.error(new RoomNotFoundException(roomId)))
                .flatMapMany(state -> {
                    boolean isParticipant = state.getPlayers().stream()
                            .anyMatch(p -> p.getSessionId().equals(sessionId))
                            || state.getSpectators().stream()
                            .anyMatch(s -> s.getSessionId().equals(sessionId));
                    if (!isParticipant) {
                        return Flux.error(new ActionNotAllowedException("not a participant in this room"));
                    }
                    return eventBus.subscribeRoom(roomId, sessionId);
                });
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    public Mono<MoveAckResponse> move(String roomId, String sessionId, MoveRequest req) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.error(new RoomNotFoundException(roomId)))
                .flatMap(state -> {
                    if (state.getStatus() == RoomStatus.ENDED) {
                        return Mono.error(new ActionNotAllowedException("game has ended"));
                    }
                    if (state.getStatus() != RoomStatus.PLAYING) {
                        return Mono.error(new ActionNotAllowedException("game has not started"));
                    }

                    PlayerInfo player = state.getPlayers().stream()
                            .filter(p -> p.getSessionId().equals(sessionId))
                            .findFirst()
                            .orElseThrow(() -> new ActionNotAllowedException("you are not a player in this room"));

                    if (!player.getSide().equals(state.getCurrentTurn())) {
                        return Mono.error(new NotYourTurnException());
                    }

                    Board board = boardFromState(state.getBoard());
                    Position fromPos = new Position(req.getFrom()[0], req.getFrom()[1]);
                    Position toPos = new Position(req.getTo()[0], req.getTo()[1]);

                    Piece movedPiece = board.getPiece(fromPos);
                    if (movedPiece == null || !movedPiece.side().name().equals(player.getSide())) {
                        return Mono.error(new InvalidMoveException());
                    }

                    Piece capturedPiece = board.getPiece(toPos);
                    Move move = new Move(fromPos, toPos, movedPiece, capturedPiece);

                    if (!ruleEngine.isValidMove(board, move)) {
                        return Mono.error(new InvalidMoveException());
                    }

                    board.makeMove(move);

                    String nextTurn = PlayerSide.PLAYER_1.name().equals(player.getSide())
                            ? PlayerSide.PLAYER_2.name() : PlayerSide.PLAYER_1.name();
                    state.setBoard(board.toBoardStateArray());
                    state.setCurrentTurn(nextTurn);
                    state.setMoveNumber(state.getMoveNumber() + 1);

                    MoveRecord record = MoveRecord.builder()
                            .from(req.getFrom())
                            .to(req.getTo())
                            .movedPiece(movedPiece.getPieceCode())
                            .capturedPiece(capturedPiece != null ? capturedPiece.getPieceCode() : null)
                            .specialEvent(move.specialEvent() != null ? move.specialEvent().name() : null)
                            .build();
                    state.getHistory().add(record);

                    Side winner = ruleEngine.isGameOver(board) ? ruleEngine.getWinner(board) : null;
                    if (winner != null) {
                        state.setStatus(RoomStatus.ENDED);
                    }
                    state.setUpdatedAt(Instant.now());

                    Side finalWinner = winner;
                    return roomRepo.save(state)
                            .doOnSuccess(v -> {
                                eventBus.emit(roomId, new StateUpdatedEvent(
                                        roomId, state.getBoard(), state.getCurrentTurn(),
                                        state.getHistory().get(state.getHistory().size() - 1),
                                        state.getStatus().name(), state.getMoveNumber()));

                                if (finalWinner != null) {
                                    eventBus.emit(roomId, new GameResultEvent(
                                            roomId, finalWinner.name(), "WIN"));
                                }
                            })
                            .then(finalWinner != null ? refreshLobby() : Mono.empty())
                            .thenReturn(MoveAckResponse.ok());
                });
    }

    // ── Leave ────────────────────────────────────────────────────────────────

    public Mono<Void> leaveRoom(String roomId, String sessionId) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.empty())
                .flatMap(state -> {
                    boolean isSpectator = state.getSpectators().stream()
                            .anyMatch(s -> s.getSessionId().equals(sessionId));

                    if (isSpectator) {
                        state.getSpectators().removeIf(s -> s.getSessionId().equals(sessionId));
                        state.setUpdatedAt(Instant.now());
                        return roomRepo.save(state)
                                .then(roomRepo.deleteSessionMapping(sessionId))
                                .then(refreshLobby())
                                .doOnSuccess(v -> eventBus.emit(roomId, new PlayersUpdatedEvent(
                                        roomId, state.getPlayers(), state.getSpectators(),
                                        state.getStatus().name())))
                                .doOnSuccess(v -> eventBus.destroySession(sessionId));
                    }

                    boolean isPlayer = state.getPlayers().stream()
                            .anyMatch(p -> p.getSessionId().equals(sessionId));

                    if (!isPlayer) return Mono.empty();

                    return handlePlayerLeave(state, sessionId);
                });
    }

    // ── Rematch ──────────────────────────────────────────────────────────────

    public Mono<Void> rematch(String roomId, String sessionId) {
        return roomRepo.findById(roomId)
                .switchIfEmpty(Mono.error(new RoomNotFoundException(roomId)))
                .flatMap(state -> {
                    if (state.getMode() != GameMode.PVP) {
                        return Mono.error(new ActionNotAllowedException("rematch is only available in PVP mode"));
                    }
                    if (state.getStatus() != RoomStatus.ENDED) {
                        return Mono.error(new ActionNotAllowedException("game has not ended"));
                    }

                    boolean bothConnected = state.getPlayers().stream()
                            .allMatch(p -> sessionRegistry.isConnected(p.getSessionId()));
                    if (!bothConnected) {
                        return Mono.error(new ActionNotAllowedException("opponent has disconnected"));
                    }

                    state.setBoard(BoardInitializer.standard().toBoardStateArray());
                    state.setCurrentTurn(PlayerSide.PLAYER_1.name());
                    state.setMoveNumber(0);
                    state.setStatus(RoomStatus.PLAYING);
                    state.getHistory().clear();
                    state.setUpdatedAt(Instant.now());

                    return roomRepo.save(state)
                            .then(refreshLobby())
                            .doOnSuccess(v -> eventBus.emit(roomId, new StateUpdatedEvent(
                                    roomId, state.getBoard(), state.getCurrentTurn(),
                                    null, state.getStatus().name(), 0)));
                });
    }

    // ── Disconnect handling ───────────────────────────────────────────────────

    public Mono<Void> handleDisconnect(String sessionId) {
        return roomRepo.findRoomIdBySession(sessionId)
                .flatMap(roomId -> roomRepo.findById(roomId)
                        .flatMap(state -> leaveRoom(roomId, sessionId)))
                .then(Mono.fromRunnable(() -> {
                    eventBus.destroySession(sessionId);
                    sessionRegistry.deregister(sessionId);
                }));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<Void> handlePlayerLeave(RoomState state, String sessionId) {
        String roomId = state.getRoomId();

        if (state.getStatus() == RoomStatus.WAITING) {
            return roomRepo.delete(roomId)
                    .then(roomRepo.deleteSessionMapping(sessionId))
                    .then(refreshLobby())
                    .doOnSuccess(v -> eventBus.destroyRoom(roomId))
                    .doOnSuccess(v -> eventBus.destroySession(sessionId));
        }

        if (state.getStatus() == RoomStatus.PLAYING) {
            String leavingSide = state.getPlayers().stream()
                    .filter(p -> p.getSessionId().equals(sessionId))
                    .map(PlayerInfo::getSide)
                    .findFirst().orElse(null);
            String winnerSide = PlayerSide.PLAYER_1.name().equals(leavingSide)
                    ? PlayerSide.PLAYER_2.name() : PlayerSide.PLAYER_1.name();

            state.setStatus(RoomStatus.ENDED);
            state.setUpdatedAt(Instant.now());
            return roomRepo.save(state)
                    .then(roomRepo.deleteSessionMapping(sessionId))
                    .then(refreshLobby())
                    .doOnSuccess(v -> eventBus.emit(roomId, new GameResultEvent(
                            roomId, winnerSide, "OPPONENT_DISCONNECTED_TIMEOUT")))
                    .doOnSuccess(v -> eventBus.destroySession(sessionId));
        }

        // ENDED — just clean up
        return roomRepo.deleteSessionMapping(sessionId)
                .doOnSuccess(v -> eventBus.destroySession(sessionId));
    }

    private Board boardFromState(String[][] cells) {
        Board board = new Board();
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[r].length; c++) {
                String code = cells[r][c];
                if (code != null) {
                    int idx = code.lastIndexOf('_');
                    Side side = Side.valueOf(code.substring(0, idx));
                    PieceType type = PieceType.valueOf(code.substring(idx + 1));
                    board.setPiece(r, c, new Piece(side, type));
                }
            }
        }
        return board;
    }

    private Mono<Void> refreshLobby() {
        return roomRepo.findAllActive()
                .collectList()
                .doOnNext(lobbyService::emitSnapshot)
                .then();
    }
}
