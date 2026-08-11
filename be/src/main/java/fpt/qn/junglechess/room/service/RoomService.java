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
import java.util.List;
import fpt.qn.junglechess.room.model.SpectatorInfo;
import fpt.qn.junglechess.room.repository.RoomStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

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
    private final BotAssignmentService botAssignmentService;

    private final ConcurrentHashMap<String, Object> roomJoinLocks = new ConcurrentHashMap<>();

    // ── Create ────────────────────────────────────────────────────────────────

    public void createRoom(CreateRoomRequest req, String sessionId, String userId, String displayName) {
        // Auto purge any previous WAITING room created by this user
        if (userId != null) {
            try {
                List<RoomState> existing = roomRepo.findAllActive();
                for (RoomState r : existing) {
                    if (r.getStatus() == RoomStatus.WAITING) {
                        boolean isUserCreator = (r.getCreatorSessionId() != null && r.getCreatorSessionId().equals(sessionId)) ||
                                (r.getPlayers() != null && r.getPlayers().stream().anyMatch(p -> userId.equals(p.getUserId())));
                        if (isUserCreator) {
                            roomRepo.delete(r.getRoomId());
                            log.info("Purged previous unstarted room {} for user {}", r.getRoomId(), userId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to purge old waiting room for user {}: {}", userId, e.getMessage());
            }
        }

        String roomId = "room-" + UuidV7.generate().toString().substring(0, 8);
        boolean isEve = req.getMode() == GameMode.EVE;
        boolean isPve = req.getMode() == GameMode.PVE;

        // PVE and EVE always allow spectators; PVP respects the request flag
        boolean allowSpectator = isPve || isEve || req.isAllowSpectator();
        boolean allowBet       = !isPve && !isEve && (allowSpectator && req.isAllowBet());
        Instant now = Instant.now();

        RoomState state = RoomState.builder()
                .roomId(roomId)
                .mode(req.getMode())
                .status(RoomStatus.WAITING)
                .allowSpectator(allowSpectator)
                .allowBet(allowBet)
                .creatorSessionId(sessionId)
                .creatorUserId(userId)
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
            String username = sessionRegistry.getUsername(sessionId);
            PlayerInfo player1 = PlayerInfo.builder()
                    .sessionId(sessionId)
                    .side(PlayerSide.PLAYER_1.name())
                    .isBot(false)
                    .userId(userId)
                    .displayName(displayName != null ? displayName : userId)
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

        // Bots are assigned here so they join while room is WAITING;
        // the creator must click Start to begin the game.
        String difficulty = req.getBotDifficulty() != null ? req.getBotDifficulty() : "MEDIUM";
        state.setBotDifficulty(difficulty);
        if (isPve) {
            String p2Username = resolveBot(req.getPlayer2BotId(), state, false);
            roomRepo.save(state);
            sendBotInvite(p2Username, req.getPlayer2BotId(), roomId, PlayerSide.PLAYER_2.name(), difficulty);
        } else if (isEve) {
            String p1Username = resolveBot(req.getPlayer1BotId(), state, true);
            String p2Username = resolveBot(req.getPlayer2BotId(), state, false);
            roomRepo.save(state);
            sendBotInvite(p1Username, req.getPlayer1BotId(), roomId, PlayerSide.PLAYER_1.name(), difficulty);
            sendBotInvite(p2Username, req.getPlayer2BotId(), roomId, PlayerSide.PLAYER_2.name(), difficulty);
        } else {
            roomRepo.save(state);
        }
    }

    /** Resolves a bot userId to its username and stores the botId in RoomState. Returns null if not found. */
    private String resolveBot(String botId, RoomState state, boolean isPlayer1) {
        if (botId == null) return null;
        String username = sessionRegistry.getUsernameByUserId(botId);
        if (username != null) {
            if (isPlayer1) state.setPlayer1BotId(botId);
            else state.setPlayer2BotId(botId);
        }
        return username;
    }

    /** Sends invite to a specific bot by username, or falls back to the default bot-worker assignment. */
    private void sendBotInvite(String botUsername, String botId, String roomId, String side, String difficulty) {
        if (botUsername != null) {
            botAssignmentService.inviteBot(botUsername, roomId, side, difficulty);
        } else {
            // botId given but bot not connected, or no botId → fall back to default worker
            botAssignmentService.assign(roomId, side, difficulty);
        }
    }

    // ── Join (human player, always fills next available slot) ─────────────────

    public void joinRoom(String roomId, String sessionId, String userId, String displayName) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getMode() == GameMode.PVE || state.getMode() == GameMode.EVE) {
            throw new ActionNotAllowedException("cannot join as player in PVE/EVE rooms — use watch instead");
        }
        if (state.getPlayers().size() >= 2) throw new RoomFullException();
        if (userId != null && state.getPlayers().stream().anyMatch(p -> userId.equals(p.getUserId()))) {
            throw new ActionNotAllowedException("Tài khoản của bạn đã ở trong phòng này");
        }

        // Determine next available side
        boolean player1Taken = state.getPlayers().stream()
                .anyMatch(p -> PlayerSide.PLAYER_1.name().equals(p.getSide()));
        String side = player1Taken ? PlayerSide.PLAYER_2.name() : PlayerSide.PLAYER_1.name();

        String username = sessionRegistry.getUsername(sessionId);
        PlayerInfo joiner = PlayerInfo.builder()
                .sessionId(sessionId)
                .side(side)
                .isBot(false)
                .userId(userId)
                .displayName(displayName != null ? displayName : userId)
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
                roomId, side, state.getMode().name(), RoomStatus.WAITING.name(),
                state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));
    }

    // ── Bot join (honors requested side, auto-starts PVE/EVE) ────────────────

    public void joinRoomAsBot(String roomId, String sessionId, String requestedSide, String userId, String displayName) {
        Object lock = roomJoinLocks.computeIfAbsent(roomId, k -> new Object());
        synchronized (lock) {
            joinRoomAsBotInternal(roomId, sessionId, requestedSide, userId, displayName);
        }
    }

    private void joinRoomAsBotInternal(String roomId, String sessionId, String requestedSide, String userId, String displayName) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);

        String side = requestedSide != null ? requestedSide : PlayerSide.PLAYER_2.name();

        // Bot reconnect: side already occupied by a bot — update session ID and re-emit state
        PlayerInfo existingBot = state.getPlayers().stream()
                .filter(p -> side.equals(p.getSide()) && p.isBot())
                .findFirst().orElse(null);
        if (existingBot != null) {
            String oldSessionId = existingBot.getSessionId();
            existingBot.setSessionId(sessionId);
            state.setUpdatedAt(Instant.now());
            roomRepo.save(state);
            roomRepo.saveSessionMapping(sessionId, roomId);
            roomRepo.saveUserMapping(userId, roomId);
            if (!oldSessionId.equals(sessionId)) roomRepo.deleteSessionMapping(oldSessionId);
            eventBus.emitToSession(sessionId, new RoomJoinedEvent(
                    roomId, side, state.getMode().name(), state.getStatus().name(),
                    state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                    state.getPlayers(), state.getSpectators()));
            log.info("Bot reconnected: room={} side={}", roomId, side);
            return;
        }

        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getPlayers().size() >= 2) throw new RoomFullException();

        boolean sideTaken = state.getPlayers().stream().anyMatch(p -> side.equals(p.getSide()));
        if (sideTaken) throw new ActionNotAllowedException("side " + side + " is already taken");

        PlayerInfo bot = PlayerInfo.builder()
                .sessionId(sessionId)
                .side(side)
                .isBot(true)
                .userId(userId)
                .displayName(displayName != null ? displayName : "Bot " + (state.getBotDifficulty() != null ? state.getBotDifficulty() : ""))
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
                roomId, side, state.getMode().name(), RoomStatus.WAITING.name(),
                state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));
    }

    // ── Start game (PVP only — explicit start by PLAYER_1) ───────────────────

    public void startGame(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.WAITING) throw new ActionNotAllowedException("game already started or ended");
        if (state.getPlayers().size() < 2) throw new ActionNotAllowedException("waiting for players to join");

        // EVE creator is a spectator; PVP/PVE creator is PLAYER_1
        boolean isCreator = sessionId.equals(state.getCreatorSessionId());
        if (!isCreator) throw new ActionNotAllowedException("only the room creator can start the game");

        autoStartGame(state);
    }

    // ── Rejoin (reconnect) ────────────────────────────────────────────────────

    public void rejoinRoom(String roomId, String newSessionId, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");

        // Check if rejoining as a player
        PlayerInfo player = state.getPlayers().stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findFirst().orElse(null);

        if (player != null) {
            String oldSessionId = player.getSessionId();
            player.setSessionId(newSessionId);
            state.setUpdatedAt(Instant.now());
            roomRepo.save(state);
            roomRepo.saveSessionMapping(newSessionId, roomId);
            roomRepo.saveUserMapping(userId, roomId);
            if (!oldSessionId.equals(newSessionId)) roomRepo.deleteSessionMapping(oldSessionId);
            eventBus.destroySession(oldSessionId);
            eventBus.emitToSession(newSessionId, new RoomJoinedEvent(
                    roomId, player.getSide(), state.getMode().name(), state.getStatus().name(),
                    state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                    state.getPlayers(), state.getSpectators()));
            return;
        }

        // Check if rejoining as a spectator (e.g. EVE creator after reload)
        SpectatorInfo spectator = state.getSpectators().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .findFirst().orElse(null);

        if (spectator != null) {
            String oldSessionId = spectator.getSessionId();
            spectator.setSessionId(newSessionId);
            state.setUpdatedAt(Instant.now());
            roomRepo.save(state);
            roomRepo.saveSessionMapping(newSessionId, roomId);
            roomRepo.saveUserMapping(userId, roomId);
            if (!oldSessionId.equals(newSessionId)) roomRepo.deleteSessionMapping(oldSessionId);
            eventBus.destroySession(oldSessionId);
            eventBus.emitToSession(newSessionId, new RoomJoinedEvent(
                    roomId, "SPECTATOR", state.getMode().name(), state.getStatus().name(),
                    state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                    state.getPlayers(), state.getSpectators()));
            return;
        }

        throw new ActionNotAllowedException("you are not in this room");
    }

    // ── Watch (as spectator) ──────────────────────────────────────────────────

    public void watchRoom(String roomId, String sessionId, String userId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (!state.isAllowSpectator()) throw new ActionNotAllowedException("spectators not allowed in this room");
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");

        // Role preemption: if user was previously a player, vacate playing slot
        boolean wasPlayer = state.getPlayers().removeIf(p -> userId != null && userId.equals(p.getUserId()));
        if (wasPlayer && state.getStatus() == RoomStatus.PLAYING) {
            state.setStatus(RoomStatus.ENDED);
            state.setWinner("DRAW");
        }

        // Deduplicate spectator entries for this user/session
        state.getSpectators().removeIf(s -> (userId != null && userId.equals(s.getUserId())) || sessionId.equals(s.getSessionId()));

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
        eventBus.emitToSession(sessionId, new RoomJoinedEvent(
                roomId, "SPECTATOR", state.getMode().name(), state.getStatus().name(),
                state.getCreatorUserId(), state.getBoard(), state.getCurrentTurn(),
                state.getPlayers(), state.getSpectators()));
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    public MoveAckResponse move(String roomId, String sessionId, MoveRequest req) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() == RoomStatus.ENDED) throw new ActionNotAllowedException("game has ended");
        if (state.getStatus() != RoomStatus.PLAYING) throw new ActionNotAllowedException("game has not started");

        // For bots, multiple players share the same session (one bot-worker connection).
        // Disambiguate by also matching the current turn so PLAYER_2 bot isn't misidentified as PLAYER_1.
        PlayerInfo player = state.getPlayers().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .filter(p -> !p.isBot() || p.getSide().equals(state.getCurrentTurn()))
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

        // Record position hash for 3-fold repetition detection
        Side nextTurnSide = Side.valueOf(nextTurn.toUpperCase());
        long posKey = fpt.qn.junglechess.game.model.ZobristTable.computeKey(board.getZobristHash(), nextTurnSide);
        state.getPositionHistory().add(posKey);
        int repCount = java.util.Collections.frequency(state.getPositionHistory(), posKey);

        Side winner = ruleEngine.isGameOver(board) ? ruleEngine.getWinner(board) : null;
        String drawReason = null;
        if (winner != null) {
            state.setStatus(RoomStatus.ENDED);
            state.setWinner(winner.name());
            state.setResultReason("WIN");
        } else if (repCount >= 3) {
            state.setStatus(RoomStatus.ENDED);
            state.setWinner("DRAW");
            drawReason = "DRAW_REPETITION";
            state.setResultReason(drawReason);
        } else if (state.getMoveNumber() >= 150) {
            state.setStatus(RoomStatus.ENDED);
            state.setWinner("DRAW");
            drawReason = "DRAW_MAX_MOVES";
            state.setResultReason(drawReason);
        }
        state.setUpdatedAt(Instant.now());

        roomRepo.save(state);

        eventBus.emit(roomId, new StateUpdatedEvent(
                roomId, state.getBoard(), state.getCurrentTurn(),
                state.getHistory().get(state.getHistory().size() - 1),
                state.getStatus().name(), state.getMoveNumber()));

        if (winner != null) {
            eventBus.emit(roomId, new GameResultEvent(roomId, winner.name(), "WIN"));
            refreshLobby();
        } else if (drawReason != null) {
            eventBus.emit(roomId, new GameResultEvent(roomId, null, drawReason));
            refreshLobby();
        }

        return MoveAckResponse.ok();
    }

    // ── Leave ────────────────────────────────────────────────────────────────

    public void leaveRoom(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) return;

        String userId = sessionRegistry.getUserId(sessionId);
        boolean isCreator = sessionId.equals(state.getCreatorSessionId()) ||
                (userId != null && state.getPlayers().stream().anyMatch(p -> PlayerSide.PLAYER_1.name().equals(p.getSide()) && userId.equals(p.getUserId())));
        boolean isPveOrEve = state.getMode() == GameMode.PVE || state.getMode() == GameMode.EVE;

        // PVE/EVE: creator leaving always terminates the room and releases bots
        if (isCreator && isPveOrEve) {
            terminatePveEveRoom(state, sessionId);
            return;
        }

        boolean isSpectator = state.getSpectators().stream()
                .anyMatch(s -> s.getSessionId().equals(sessionId) || (userId != null && userId.equals(s.getUserId())));

        if (isSpectator) {
            state.getSpectators().removeIf(s -> s.getSessionId().equals(sessionId) || (userId != null && userId.equals(s.getUserId())));
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
                .anyMatch(p -> p.getSessionId().equals(sessionId) || (userId != null && userId.equals(p.getUserId())));

        if (isCreator || isPlayer) {
            handlePlayerLeave(state, sessionId, userId);
            return;
        }
    }

    private void terminatePveEveRoom(RoomState state, String creatorSessionId) {
        String roomId = state.getRoomId();
        // Notify bots and spectators so their sessions self-terminate
        eventBus.emit(roomId, new GameResultEvent(roomId, null, "ROOM_CANCELLED"));
        // Clean up all player session mappings (bots share one session, dedup with stream)
        state.getPlayers().stream()
                .map(PlayerInfo::getSessionId).distinct()
                .forEach(roomRepo::deleteSessionMapping);
        state.getPlayers().stream()
                .map(PlayerInfo::getUserId).filter(id -> id != null).distinct()
                .forEach(roomRepo::deleteUserMapping);
        state.getSpectators().forEach(s -> roomRepo.deleteSessionMapping(s.getSessionId()));
        roomRepo.deleteSessionMapping(creatorSessionId);
        roomRepo.delete(roomId);
        refreshLobby();
        log.info("PVE/EVE room {} terminated by creator leaving", roomId);
    }

    // ── Sync (client requests current state after subscribing) ───────────────

    public void syncRoom(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) return;
        eventBus.emitToSession(sessionId, new PlayersUpdatedEvent(
                roomId, state.getPlayers(), state.getSpectators(), state.getStatus().name()));
    }

    // ── Rematch ──────────────────────────────────────────────────────────────

    public void rematch(String roomId, String sessionId) {
        RoomState state = roomRepo.findById(roomId);
        if (state == null) throw new RoomNotFoundException(roomId);
        if (state.getStatus() != RoomStatus.ENDED) throw new ActionNotAllowedException("game has not ended");

        boolean isCreator = sessionId.equals(state.getCreatorSessionId());

        if (state.getMode() == GameMode.PVP) {
            boolean bothConnected = state.getPlayers().stream()
                    .allMatch(p -> sessionRegistry.isConnected(p.getSessionId()));
            if (!bothConnected) throw new ActionNotAllowedException("opponent has disconnected");
        } else {
            if (!isCreator) throw new ActionNotAllowedException("only the room creator can rematch");
        }

        // Reset board and remove finished bot players so they re-join fresh
        state.setBoard(BoardInitializer.standard().toBoardStateArray());
        state.setCurrentTurn(PlayerSide.PLAYER_1.name());
        state.setMoveNumber(0);
        state.setStatus(RoomStatus.WAITING);
        state.getHistory().clear();
        state.getPositionHistory().clear();
        state.setUpdatedAt(Instant.now());

        if (state.getMode() == GameMode.PVE) {
            state.getPlayers().removeIf(PlayerInfo::isBot);
        } else if (state.getMode() == GameMode.EVE) {
            state.getPlayers().clear();
        }

        roomRepo.save(state);
        refreshLobby();

        eventBus.emit(roomId, new PlayersUpdatedEvent(
                roomId, state.getPlayers(), state.getSpectators(), RoomStatus.WAITING.name()));

        // Re-invite bots with the same difficulty and the same bot IDs as the original game
        String difficulty = state.getBotDifficulty() != null ? state.getBotDifficulty() : "MEDIUM";
        if (state.getMode() == GameMode.PVE) {
            String p2Username = state.getPlayer2BotId() != null
                    ? sessionRegistry.getUsernameByUserId(state.getPlayer2BotId()) : null;
            sendBotInvite(p2Username, state.getPlayer2BotId(), roomId, PlayerSide.PLAYER_2.name(), difficulty);
        } else if (state.getMode() == GameMode.EVE) {
            String p1Username = state.getPlayer1BotId() != null
                    ? sessionRegistry.getUsernameByUserId(state.getPlayer1BotId()) : null;
            String p2Username = state.getPlayer2BotId() != null
                    ? sessionRegistry.getUsernameByUserId(state.getPlayer2BotId()) : null;
            sendBotInvite(p1Username, state.getPlayer1BotId(), roomId, PlayerSide.PLAYER_1.name(), difficulty);
            sendBotInvite(p2Username, state.getPlayer2BotId(), roomId, PlayerSide.PLAYER_2.name(), difficulty);
        } else {
            // PVP: immediately start again (no bots to wait for)
            state.setStatus(RoomStatus.PLAYING);
            roomRepo.save(state);
            eventBus.emit(roomId, new StateUpdatedEvent(
                    roomId, state.getBoard(), state.getCurrentTurn(),
                    null, RoomStatus.PLAYING.name(), 0));
        }
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
        // StateUpdatedEvent signals bots (and spectators) of the initial board state and first turn.
        // Without this, bots only listen for STATE_UPDATED and never learn the game has begun.
        eventBus.emit(state.getRoomId(), new StateUpdatedEvent(
                state.getRoomId(), state.getBoard(), state.getCurrentTurn(),
                null, RoomStatus.PLAYING.name(), state.getMoveNumber()));
        log.info("Room {} started (mode={})", state.getRoomId(), state.getMode());
    }

    private void handleDisconnectNow(String sessionId) {
        try {
            String roomId = roomRepo.findRoomIdBySession(sessionId);
            if (roomId != null) {
                RoomState state = roomRepo.findById(roomId);
                // Keep the room alive so the user can reload/rejoin.
                // Only remove the session mapping so a fresh WebSocket session
                // (browser refresh, reconnection) can rejoin the same room.
                if (state != null &&
                        (state.getStatus() == RoomStatus.WAITING || state.getStatus() == RoomStatus.PLAYING)) {
                    roomRepo.deleteSessionMapping(sessionId);
                    log.info("Session {} disconnected — room {} preserved for rejoin (status={})",
                            sessionId, roomId, state.getStatus());
                } else {
                    leaveRoom(roomId, sessionId);
                }
            }
        } finally {
            eventBus.destroySession(sessionId);
            sessionRegistry.deregister(sessionId);
        }
    }

    private void handlePlayerLeave(RoomState state, String sessionId, String currentUserId) {
        String roomId = state.getRoomId();
        String userId = currentUserId != null ? currentUserId : state.getPlayers().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .map(PlayerInfo::getUserId)
                .findFirst().orElse(null);

        if (state.getStatus() == RoomStatus.WAITING) {
            // Notify bots so their sessions self-terminate before we delete the room
            if (state.getMode() == GameMode.PVE || state.getMode() == GameMode.EVE) {
                eventBus.emit(roomId, new GameResultEvent(roomId, null, "ROOM_CANCELLED"));
            }
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
                    .filter(p -> p.getSessionId().equals(sessionId) || (userId != null && userId.equals(p.getUserId())))
                    .map(PlayerInfo::getSide)
                    .findFirst().orElse(null);
            String winnerSide = PlayerSide.PLAYER_1.name().equals(leavingSide)
                    ? PlayerSide.PLAYER_2.name() : PlayerSide.PLAYER_1.name();

            state.setStatus(RoomStatus.ENDED);
            state.setWinner(winnerSide);
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
        board.recomputeZobrist();
        return board;
    }

    private void refreshLobby() {
        lobbyService.emitSnapshot(roomRepo.findAllActive());
    }
}
