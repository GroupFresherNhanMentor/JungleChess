package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.common.util.UuidV7;
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
import fpt.qn.junglechess.room.dto.event.RoomCreatedEvent;
import fpt.qn.junglechess.room.dto.event.RoomJoinedEvent;
import fpt.qn.junglechess.room.dto.event.StateUpdatedEvent;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.request.MoveRequest;
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
    private final DisconnectScheduler disconnectScheduler;
    private final BotWorkerClient botWorkerClient;

    // ── Create ────────────────────────────────────────────────────────────────

    public void createRoom(CreateRoomRequest req, String sessionId, String userId) {
        String roomId = "room-" + UuidV7.generate().toString().substring(0, 8);
        String difficulty = req.getBotDifficulty() != null ? req.getBotDifficulty() : "MEDIUM";
        boolean isEve = req.getMode() == GameMode.EVE;

        boolean allowSpectator = isEve || req.isAllowSpectator();
        boolean allowBet       = isEve || (allowSpectator && req.isAllowBet());
        Instant now = Instant.now();

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

        String yourSide;
        if (isEve) {
            // Creator watches; two bots will fill both player slots
            SpectatorInfo creator = SpectatorInfo.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .build();
            state.getSpectators().add(creator);
            yourSide = "SPECTATOR";
        } else {
            // PVP or PVE: creator is PLAYER_1
            PlayerInfo player1 = PlayerInfo.builder()
                    .sessionId(sessionId)
                    .side(PlayerSide.PLAYER_1.name())
                    .isBot(false)
                    .userId(userId)
                    .build();
            state.getPlayers().add(player1);
            yourSide = PlayerSide.PLAYER_1.name();
        }

        roomRepo.save(state);
        roomRepo.saveSessionMapping(sessionId, roomId);
        roomRepo.saveUserMapping(userId, roomId);
        refreshLobby();

        eventBus.emitToSession(sessionId, new RoomCreatedEvent(
                roomId, yourSide, req.getMode().name(),
                RoomStatus.WAITING.name(), allowSpectator, allowBet,
                state.getPlayers(), state.getSpectators()));

        // Trigger bot-worker for PVE (1 bot) and EVE (2 bots)
        if (req.getMode() == GameMode.PVE) {
            botWorkerClient.assign(roomId, PlayerSide.PLAYER_2.name(), difficulty);
        } else if (isEve) {
            botWorkerClient.assign(roomId, PlayerSide.PLAYER_1.name(), difficulty);
            botWorkerClient.assign(roomId, PlayerSide.PLAYER_2.name(), difficulty);
        }
    }

    // ── Join (human player, always fills next available slot) ─────────────────

    public void joinRoom(String roomId, String sessionId, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getPlayers().size() >= 2) throw new RoomFullException();

        // Determine next available side
        boolean player1Taken = state.getPlayers().stream()
                .anyMatch(p -> PlayerSide.PLAYER_1.name().equals(p.getSide()));
        String side = player1Taken ? PlayerSide.PLAYER_2.name() : PlayerSide.PLAYER_1.name();

        PlayerInfo joiner = PlayerInfo.builder()
                .sessionId(sessionId)
                .side(side)
                .isBot(false)
                .userId(userId)
                .build();
        state.getPlayers().add(joiner);
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);
        roomRepo.saveSessionMapping(sessionId, roomId);
        roomRepo.saveUserMapping(userId, roomId);
        refreshLobby();

        eventBus.emit(roomId, new PlayersUpdatedEvent(
                roomId, state.getPlayers(), state.getSpectators(), RoomStatus.WAITING.name()));
        eventBus.emitToSession(sessionId, new RoomJoinedEvent(
                roomId, side, RoomStatus.WAITING.name(),
                state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));
    }

    // ── Bot join (honors requested side, auto-starts PVE/EVE) ────────────────

    public void joinRoomAsBot(String roomId, String sessionId, String requestedSide, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getPlayers().size() >= 2) throw new RoomFullException();

        String side = requestedSide != null ? requestedSide : PlayerSide.PLAYER_2.name();
        boolean sideTaken = state.getPlayers().stream().anyMatch(p -> side.equals(p.getSide()));
        if (sideTaken) throw new ActionNotAllowedException("side " + side + " is already taken");

        PlayerInfo bot = PlayerInfo.builder()
                .sessionId(sessionId)
                .side(side)
                .isBot(true)
                .userId(userId)
                .build();
        state.getPlayers().add(bot);
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);
        roomRepo.saveSessionMapping(sessionId, roomId);
        roomRepo.saveUserMapping(userId, roomId);
        refreshLobby();

        eventBus.emit(roomId, new PlayersUpdatedEvent(
                roomId, state.getPlayers(), state.getSpectators(), RoomStatus.WAITING.name()));
        eventBus.emitToSession(sessionId, new RoomJoinedEvent(
                roomId, side, RoomStatus.WAITING.name(),
                state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));

        // Auto-start when room is full in PVE or EVE
        if (state.getPlayers().size() == 2 && state.getMode() != GameMode.PVP) {
            autoStartGame(state);
        }
    }

    // ── Start game (PVP only — explicit start by PLAYER_1) ───────────────────

    public void startGame(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getPlayers().size() < 2) throw new ActionNotAllowedException("need 2 players to start");
        if (state.getMode() != GameMode.PVP) throw new ActionNotAllowedException("PVE/EVE games start automatically");

        boolean isCreator = state.getPlayers().stream()
                .anyMatch(p -> p.getSessionId().equals(sessionId) && PlayerSide.PLAYER_1.name().equals(p.getSide()));
        if (!isCreator) throw new ActionNotAllowedException("only the room creator can start the game");

        autoStartGame(state);
    }

    // ── Rejoin (reconnect) ────────────────────────────────────────────────────

    public void rejoinRoom(String roomId, String newSessionId, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");

        PlayerInfo player = state.getPlayers().stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findFirst()
                .orElseThrow(() -> new ActionNotAllowedException("you are not a player in this room"));

        String oldSessionId = player.getSessionId();
        player.setSessionId(newSessionId);
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);
        roomRepo.saveSessionMapping(newSessionId, roomId);
        roomRepo.saveUserMapping(userId, roomId);
        roomRepo.deleteSessionMapping(oldSessionId);
        eventBus.destroySession(oldSessionId);

        eventBus.emitToSession(newSessionId, new RoomJoinedEvent(
                roomId, player.getSide(), state.getStatus().name(),
                state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));
    }

    // ── Watch (as spectator) ──────────────────────────────────────────────────

    public void watchRoom(String roomId, String sessionId, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (!state.isAllowSpectator()) throw new ActionNotAllowedException("spectators not allowed in this room");
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");

        SpectatorInfo spectator = SpectatorInfo.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        state.getSpectators().add(spectator);
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);
        roomRepo.saveSessionMapping(sessionId, roomId);
        refreshLobby();

        eventBus.emit(roomId, new PlayersUpdatedEvent(
                roomId, state.getPlayers(), state.getSpectators(), state.getStatus().name()));
        eventBus.emitToSession(sessionId, new StateUpdatedEvent(
                roomId, state.getBoard(), state.getCurrentTurn(),
                state.getHistory().isEmpty() ? null : state.getHistory().get(state.getHistory().size() - 1),
                state.getStatus().name(), state.getMoveNumber()));
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    public MoveAckResponse move(String roomId, String sessionId, MoveRequest req) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");
        if (state.getStatus() != RoomStatus.PLAYING) throw new ActionNotAllowedException("game has not started");

        PlayerInfo player = state.getPlayers().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new ActionNotAllowedException("you are not a player in this room"));

        if (!player.getSide().equals(state.getCurrentTurn())) throw new NotYourTurnException();

        Board board = boardFromState(state.getBoard());
        Position fromPos = new Position(req.getFrom()[0], req.getFrom()[1]);
        Position toPos   = new Position(req.getTo()[0], req.getTo()[1]);

        Piece movedPiece = board.getPiece(fromPos);
        if (movedPiece == null || !movedPiece.side().name().equals(player.getSide())) {
            throw new InvalidMoveException();
        }

        Piece capturedPiece = board.getPiece(toPos);
        Move move = new Move(fromPos, toPos, movedPiece, capturedPiece);
        if (!ruleEngine.isValidMove(board, move)) throw new InvalidMoveException();

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
        if (winner != null) state.setStatus(RoomStatus.ENDED);
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);

        eventBus.emit(roomId, new StateUpdatedEvent(
                roomId, state.getBoard(), state.getCurrentTurn(),
                state.getHistory().get(state.getHistory().size() - 1),
                state.getStatus().name(), state.getMoveNumber()));

        if (winner != null) {
            eventBus.emit(roomId, new GameResultEvent(roomId, winner.name(), "WIN"));
            refreshLobby();
        }

        return MoveAckResponse.ok();
    }

    // ── Leave ────────────────────────────────────────────────────────────────

    public void leaveRoom(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) return;

        boolean isSpectator = state.getSpectators().stream()
                .anyMatch(s -> s.getSessionId().equals(sessionId));

        if (isSpectator) {
            state.getSpectators().removeIf(s -> s.getSessionId().equals(sessionId));
            state.setUpdatedAt(Instant.now());
            roomRepo.save(state);
            roomRepo.deleteSessionMapping(sessionId);
            refreshLobby();
            eventBus.emit(roomId, new PlayersUpdatedEvent(
                    roomId, state.getPlayers(), state.getSpectators(), state.getStatus().name()));
            eventBus.destroySession(sessionId);
            return;
        }

        boolean isPlayer = state.getPlayers().stream()
                .anyMatch(p -> p.getSessionId().equals(sessionId));
        if (!isPlayer) return;

        handlePlayerLeave(state, sessionId);
    }

    // ── Rematch ──────────────────────────────────────────────────────────────

    public void rematch(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getMode() != GameMode.PVP) throw new ActionNotAllowedException("rematch is only available in PVP mode");
        if (state.getStatus() != RoomStatus.ENDED) throw new ActionNotAllowedException("game has not ended");

        boolean bothConnected = state.getPlayers().stream()
                .allMatch(p -> sessionRegistry.isConnected(p.getSessionId()));
        if (!bothConnected) throw new ActionNotAllowedException("opponent has disconnected");

        state.setBoard(BoardInitializer.standard().toBoardStateArray());
        state.setCurrentTurn(PlayerSide.PLAYER_1.name());
        state.setMoveNumber(0);
        state.setStatus(RoomStatus.PLAYING);
        state.getHistory().clear();
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);
        refreshLobby();

        eventBus.emit(roomId, new StateUpdatedEvent(
                roomId, state.getBoard(), state.getCurrentTurn(),
                null, state.getStatus().name(), 0));
    }

    // ── Disconnect handling ───────────────────────────────────────────────────

    public void handleDisconnect(String sessionId) {
        String userId = sessionRegistry.getUserId(sessionId);

        if (userId == null) {
            String roomId = roomRepo.findRoomIdBySession(sessionId);
            if (roomId != null) leaveRoom(roomId, sessionId);
            eventBus.destroySession(sessionId);
            sessionRegistry.deregister(sessionId);
            return;
        }

        disconnectScheduler.schedule(userId, () -> handleDisconnectNow(sessionId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void autoStartGame(RoomState state) {
        state.setStatus(RoomStatus.PLAYING);
        state.setUpdatedAt(Instant.now());
        roomRepo.save(state);
        refreshLobby();
        eventBus.emit(state.getRoomId(), new PlayersUpdatedEvent(
                state.getRoomId(), state.getPlayers(), state.getSpectators(), RoomStatus.PLAYING.name()));
        log.info("Room {} auto-started (mode={})", state.getRoomId(), state.getMode());
    }

    private void handleDisconnectNow(String sessionId) {
        try {
            String roomId = roomRepo.findRoomIdBySession(sessionId);
            if (roomId != null) leaveRoom(roomId, sessionId);
        } finally {
            eventBus.destroySession(sessionId);
            sessionRegistry.deregister(sessionId);
        }
    }

    private void handlePlayerLeave(RoomState state, String sessionId) {
        String roomId = state.getRoomId();
        String userId = state.getPlayers().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .map(PlayerInfo::getUserId)
                .findFirst().orElse(null);

        if (state.getStatus() == RoomStatus.WAITING) {
            roomRepo.delete(roomId);
            roomRepo.deleteSessionMapping(sessionId);
            if (userId != null) roomRepo.deleteUserMapping(userId);
            refreshLobby();
            eventBus.destroyRoom(roomId);
            eventBus.destroySession(sessionId);
            return;
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

            roomRepo.save(state);
            roomRepo.deleteSessionMapping(sessionId);
            if (userId != null) roomRepo.deleteUserMapping(userId);
            refreshLobby();

            eventBus.emit(roomId, new GameResultEvent(roomId, winnerSide, "OPPONENT_DISCONNECTED_TIMEOUT"));
            eventBus.destroySession(sessionId);
            return;
        }

        // ENDED — just clean up
        roomRepo.deleteSessionMapping(sessionId);
        if (userId != null) roomRepo.deleteUserMapping(userId);
        eventBus.destroySession(sessionId);
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

    private void refreshLobby() {
        lobbyService.emitSnapshot(roomRepo.findAllActive());
    }
}
