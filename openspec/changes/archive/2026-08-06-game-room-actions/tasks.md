# Tasks: In-Game Room Actions (RSocket)

## Implementation Order

Build bottom-up: models → persistence → domain logic → services → controllers.  
Each task should be independently compilable and testable before moving to the next.

---

## Task 1 — Game Domain Models ✓

**Files to create**:
- `game/model/PieceType.java` — enum: RAT, CAT, DOG, WOLF, LEOPARD, TIGER, LION, ELEPHANT (ranks 1–8)
- `game/model/PlayerSide.java` — enum: PLAYER_1, PLAYER_2
- `game/model/Piece.java` — record: PieceType type, PlayerSide side; pieceCode() → "P1_ELEPHANT"
- `game/model/Position.java` — record: int row, int col; validation 0–8 rows, 0–6 cols
- `game/model/Board.java` — wraps `String[9][7]`; `get(Position)`, `set(Position, String)`, `isEmpty(Position)`
- `game/model/MoveResult.java` — record: boolean valid, String specialEvent (nullable)
- `game/model/WinResult.java` — record: PlayerSide winner, String reason

**Verification**: Unit test `Position` bounds, `Board` get/set, `Piece.pieceCode()` output.

---

## Task 2 — BoardInitializer ✓

**Files to create**:
- `game/rule/BoardInitializer.java` — `standard()` returns a `Board` with pieces in starting positions per Jungle Chess rules

Standard layout (row 0 = PLAYER_1 back rank, row 8 = PLAYER_2 back rank):
```
P1 back rank (row 0):  LION(0,0), TIGER(0,6)
P1 pieces (rows 1-2):  DOG(1,1), CAT(1,5), RAT(2,0), LEOPARD(2,2), WOLF(2,4), ELEPHANT(2,6)
P1 den: (3,3)
P1 traps: (2,3), (3,2), (3,4)

P2 back rank (row 8):  TIGER(8,0), LION(8,6)
P2 pieces (rows 6-7):  ELEPHANT(6,0), WOLF(6,2), LEOPARD(6,4), RAT(6,6), CAT(7,1), DOG(7,5)
P2 den: (5,3)
P2 traps: (5,2), (5,4), (6,3)

River cells: rows 3-5, cols 1-2 and 4-5
```

**Verification**: `BoardInitializer.standard()` returns non-null board with all 16 pieces placed correctly. Count pieces per side = 8.

---

## Task 3 — GameRuleEngine (skeleton + move validation) ✓

**Files to create**:
- `game/rule/GameRuleEngine.java`

Implement:
- `MoveResult validate(String[][] board, int[] from, int[] to, String side)`
  - Source cell must contain a piece belonging to `side`
  - Destination must be within bounds
  - Cannot move into own den
  - Standard adjacency move (up/down/left/right, 1 cell)
  - Trap rule: piece in opponent's trap loses rank for capture checks
  - River rule: non-RAT, non-LION, non-TIGER cannot enter river; LION and TIGER can jump over river (full row/col of river cells) if no RAT is blocking
  - RAT rule: RAT can enter river; RAT in river cannot capture ELEPHANT on land
  - Capture: piece can capture opponent piece of equal or lower rank (with trap modifier)
  - `specialEvent`: set `RIVER_JUMP` for lion/tiger jump, `TRAP_NEUTRALIZED` when capture in trap
- `Optional<WinResult> checkWin(String[][] board, String lastMoveSide)`
  - `DEN_REACHED`: check if any opponent piece is in caller's den
  - `NO_VALID_MOVE`: check if opponent has zero legal moves
- `List<int[]> validMoves(String[][] board, int[] from, String side)`
  - Returns all destinations where `validate()` returns `valid=true`

**Verification**: Unit tests for:
- Basic adjacency move
- River jump (LION/TIGER) with and without RAT blocker
- RAT entering river
- Capture with rank check
- Trap neutralization
- Den entry win detection
- No valid moves win detection

---

## Task 4 — Room Domain Models ✓

**Files to create**:
- `room/model/RoomStatus.java` — enum: WAITING, PLAYING, ENDED
- `room/model/GameMode.java` — enum: PVP, PVE, EVE
- `room/model/PlayerInfo.java` — record: sessionId, side, isBot, userId
- `room/model/SpectatorInfo.java` — record: sessionId, userId
- `room/model/MoveRecord.java` — record: int[] from, int[] to, String movedPiece, String capturedPiece, String specialEvent
- `room/model/RoomState.java` — all fields per spec §5; include `updatedAt` for Redis TTL refresh

**Verification**: Serialize/deserialize `RoomState` to/from JSON using Jackson. All fields round-trip correctly.

---

## Task 5 — RoomState DTOs and Events ✓

**Files to create** (requests):
- `room/dto/request/CreateRoomRequest.java` — mode, allowSpectator, allowBet; validation: allowBet=true requires allowSpectator=true
- `room/dto/request/MoveRequest.java` — int[] from, int[] to; validation: length=2 each

**Files to create** (responses):
- `room/dto/response/CreateRoomResponse.java`
- `room/dto/response/JoinRoomResponse.java`
- `room/dto/response/MoveAckResponse.java` — boolean accepted

**Files to create** (events):
- `room/dto/event/RoomEvent.java` — base class/interface with `String type`
- `room/dto/event/StateUpdatedEvent.java` — type="STATE_UPDATED", roomId, board, currentTurn, lastMove, status, moveNumber
- `room/dto/event/PlayersUpdatedEvent.java` — type="PLAYERS_UPDATED", roomId, players, spectators, status
- `room/dto/event/GameResultEvent.java` — type="GAME_RESULT", roomId, winner (nullable), reason, endedAt
- `room/dto/event/RoomErrorEvent.java` — type="ROOM_ERROR", errorCode, message, context (Object)

**Verification**: All DTOs serialize to JSON with correct `type` field. `CreateRoomRequest` validation rejects `allowBet=true, allowSpectator=false`.

---

## Task 6 — Redis Repository ✓

**Files to create**:
- `room/repository/RoomStateRepository.java`

Methods:
- `Mono<RoomState> findById(String roomId)`
- `Mono<Void> save(RoomState state)` — serializes to JSON, sets TTL 2h
- `Mono<Void> delete(String roomId)`
- `Mono<String> findRoomIdBySession(String sessionId)`
- `Mono<Void> saveSessionMapping(String sessionId, String roomId)`
- `Mono<Void> deleteSessionMapping(String sessionId)`
- `Flux<RoomState> findAllActive()` — for lobby snapshot rebuild (scan `room:*` keys)

Use `ReactiveRedisTemplate<String, String>` with Jackson serialization of `RoomState`.

**Verification**: Integration test (Testcontainers Redis): save → findById → delete cycle. Session mapping save/find/delete.

---

## Task 7 — RoomEventBus ✓

**Files to create**:
- `room/service/RoomEventBus.java`

```
ConcurrentHashMap<String, Sinks.Many<RoomEvent>> roomSinks
ConcurrentHashMap<String, Sinks.Many<RoomEvent>> sessionErrorSinks

getSink(roomId) → Sinks.many().multicast().onBackpressureBuffer()
getSessionSink(sessionId) → Sinks.many().multicast().onBackpressureBuffer()
emit(roomId, event)
emitToSession(sessionId, event)
destroyRoom(roomId) → complete sink, remove from map
destroySession(sessionId) → complete sink, remove from map
```

**Verification**: Unit test — two subscribers to same room Sink both receive emitted event. Session sink emits only to correct subscriber.

---

## Task 8 — LobbyService ✓

**Files to create**:
- `room/service/LobbyService.java`

```
Sinks.Many<LobbySnapshot> lobbySink = Sinks.many().replay().latest()

emitSnapshot(List<RoomState> activeRooms)
  → filters: WAITING rooms + PLAYING+allowSpectator rooms
  → maps to LobbySnapshot entries
  → emits to lobbySink

Flux<LobbySnapshot> stream() → lobbySink.asFlux()
```

Called by `RoomService` after every state change that affects lobby visibility.

**Verification**: Unit test — subscribe to stream, call emitSnapshot, assert correct filter (ENDED rooms excluded, PLAYING+noSpectator excluded).

---

## Task 9 — RoomService ✓

**Files to create**:
- `room/service/RoomService.java`
- `room/exception/RoomNotFoundException.java`
- `room/exception/RoomFullException.java`
- `room/exception/NotYourTurnException.java`
- `room/exception/InvalidMoveException.java`
- `room/exception/ActionNotAllowedException.java`

Implement all state transitions per design §6:
- `createRoom(CreateRoomRequest, sessionId)` → `Mono<CreateRoomResponse>`
- `joinRoom(roomId, sessionId, isBot, userId)` → `Mono<JoinRoomResponse>`
- `watchRoom(roomId, sessionId, userId)` → `Mono<Void>`
- `subscribeRoom(roomId, sessionId)` → `Flux<RoomEvent>`
- `move(roomId, sessionId, MoveRequest)` → `Mono<MoveAckResponse>`
- `leaveRoom(roomId, sessionId)` → `Mono<Void>`
- `rematch(roomId, sessionId)` → `Mono<Void>`
- `handleDisconnect(sessionId)` → `Mono<Void>`

**Verification**: Unit tests with mocked `RoomStateRepository`, `RoomEventBus`, `GameRuleEngine`, `LobbyService`:
- `joinRoom` on full room → `RoomFullException`
- `move` out of turn → `NotYourTurnException`
- `move` invalid → `InvalidMoveException`
- `rematch` in EVE mode → `ActionNotAllowedException`
- `rematch` when opponent disconnected → `ActionNotAllowedException`
- `leaveRoom` during PLAYING → `GAME_RESULT` event emitted
- `handleDisconnect` as spectator → `PLAYERS_UPDATED` emitted, no `GAME_RESULT`

---

## Task 10 — DockerBotService + EveAdminController ✓

**Files to create**:
- `eve/service/DockerBotService.java`
- `eve/controller/EveAdminController.java`

`DockerBotService`:
- `spawnBotPair(String roomId)` — starts two containers via Docker (ProcessBuilder or docker-java)
- `inspectContainer(String containerId)` → `ContainerHealth` (RUNNING/EXITED/ERROR/UNKNOWN)
- Config: `app.eve.bot-image`, `app.eve.server-url` from `application.yml`

`EveAdminController`:
- `POST /api/admin/room/eve` (REST, WebFlux `@RestController`) — calls `RoomService.createRoom` with `mode=EVE`, then `DockerBotService.spawnBotPair`
- `@MessageMapping("admin.eve.rooms")` — returns `Flux<EveRoomSnapshot>` from a dedicated EVE room Sink in `DockerBotService`, merged with 10s health poll interval

**Verification**:
- Unit test `EveAdminController` POST — mock `DockerBotService.spawnBotPair` called once with correct roomId.
- Manual test: create EVE room, observe two bots joining in logs.

---

## Task 11 — RoomController + LobbyController ✓

**Files to create**:
- `room/controller/RoomController.java`
- `lobby/controller/LobbyController.java`

`RoomController` — all `@MessageMapping` routes per design §3:
- Each method delegates directly to `RoomService`
- Inject `RSocketRequester` to extract `sessionId` from connection metadata
- Exception handling: catch domain exceptions, emit `RoomErrorEvent` to session error Sink via `RoomEventBus.emitToSession()`

`LobbyController`:
- `@MessageMapping("lobby.rooms")` → `Flux<LobbySnapshot>` from `LobbyService.stream()`

**Verification**:
- Integration test (Spring Boot test slice with RSocket client):
  - Connect two clients, first creates PVP room, second joins → both receive `PLAYERS_UPDATED` with `status=PLAYING`
  - First client sends valid move → both receive `STATE_UPDATED`
  - First client sends invalid move → only first client receives `ROOM_ERROR`
  - Lobby stream subscriber receives update after room creation

---

## Task 12 — Session Registry + Disconnect Handling ✓

**Files to create** (or update `RoomController`/a new `SessionRegistry.java`):
- `room/service/SessionRegistry.java` — `Map<sessionId, RSocketRequester>` in-memory; register/deregister

Update `@ConnectMapping` in `RoomController` (or a dedicated `ConnectionController`):
```
onConnect(RSocketRequester requester):
  sessionId = extractSessionId(requester)
  sessionRegistry.register(sessionId, requester)
  requester.rsocket().onClose().doFinally(_ → roomService.handleDisconnect(sessionId)).subscribe()
```

`extractSessionId`: read from STOMP-style CONNECT metadata or generate a UUID tied to the requester.

**Verification**:
- Integration test: client connects, creates room, kills connection → `handleDisconnect` called → lobby stream emits room removed.

---

## Task 13 — Update openspec/config.yaml (done)

Already updated in this change — STOMP references replaced with RSocket, PVP_LOCAL and botDifficulty removed.

---

## Acceptance Criteria

| Scenario | Expected |
|----------|----------|
| PVP room created → 2nd player joins | Both see `PLAYERS_UPDATED(PLAYING)`. Lobby updates. |
| Valid move sent | All room subscribers receive `STATE_UPDATED`. Sender receives `MoveAckResponse(accepted=true)`. |
| Invalid move sent | Sender receives `ROOM_ERROR(INVALID_MOVE)`. Others see nothing. |
| Move out of turn | Sender receives `ROOM_ERROR(NOT_YOUR_TURN)`. |
| Spectator joins mid-game | Spectator immediately receives current `STATE_UPDATED`. All see `PLAYERS_UPDATED`. |
| Spectator sends move | Spectator receives `ROOM_ERROR(ACTION_NOT_ALLOWED)`. |
| Player leaves during PLAYING | All subscribers receive `GAME_RESULT(OPPONENT_DISCONNECTED_TIMEOUT)`. |
| Browser closes (disconnect) | Same as leave — `handleDisconnect` fires, room ends if PLAYING. |
| Win condition reached | All receive `STATE_UPDATED` then `GAME_RESULT(DEN_REACHED or NO_VALID_MOVE)`. |
| Rematch in EVE mode | Sender receives `ACTION_NOT_ALLOWED`. |
| Rematch in PVP, both connected | All receive `STATE_UPDATED` with reset board. |
| EVE room created | Two bot containers start. Bots join, game begins automatically. |
| Admin EVE stream | Shows container health. Updates on join/result/health change. |
| Lobby stream subscriber | Receives snapshot on room create, join (if PLAYING+noSpectator hides it), watch-enabled game appears. |
