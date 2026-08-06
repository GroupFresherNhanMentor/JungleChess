# Design: In-Game Room Actions (RSocket)

## 1. Architecture Overview

```
CLIENT (human / bot)
  │
  │  RSocket over WebSocket  →  wss://.../rsocket
  │
RSOCKET LAYER (Spring Boot)
  │
  ├── RoomController          @MessageMapping("room.*")
  ├── LobbyController         @MessageMapping("lobby.*")
  └── EveAdminController      @MessageMapping("admin.eve.*")
       + REST POST /api/admin/room/eve
  │
SERVICE LAYER
  ├── RoomService             orchestrates all room state transitions
  ├── RoomEventBus            manages per-room Sinks.Many<RoomEvent>
  ├── LobbyService            manages global Sinks.Many<LobbySnapshot>
  └── DockerBotService        triggers Docker containers for EVE rooms
  │
DOMAIN LAYER
  ├── GameRuleEngine          pure logic — validate moves, detect win
  └── BoardInitializer        produces the standard Jungle Chess starting board
  │
PERSISTENCE LAYER
  └── RoomStateRepository     reads/writes RoomState JSON in Redis (reactive)
  │
INFRASTRUCTURE
  ├── Redis                   room:{roomId} → RoomState JSON
  │                           session:{sessionId} → roomId
  └── Docker daemon           spawns/tracks EVE bot containers
```

---

## 2. Package Structure

```
fpt.qn.junglechess
├── room
│   ├── controller
│   │   └── RoomController.java
│   ├── service
│   │   ├── RoomService.java
│   │   ├── RoomEventBus.java
│   │   └── LobbyService.java
│   ├── repository
│   │   └── RoomStateRepository.java
│   ├── model
│   │   ├── RoomState.java
│   │   ├── RoomStatus.java          (WAITING, PLAYING, ENDED)
│   │   ├── GameMode.java            (PVP, PVE, EVE)
│   │   ├── PlayerSide.java          (PLAYER_1, PLAYER_2)
│   │   ├── PlayerInfo.java
│   │   └── SpectatorInfo.java
│   ├── dto
│   │   ├── request
│   │   │   ├── CreateRoomRequest.java
│   │   │   └── MoveRequest.java
│   │   ├── response
│   │   │   ├── CreateRoomResponse.java
│   │   │   ├── JoinRoomResponse.java
│   │   │   └── MoveAckResponse.java
│   │   └── event
│   │       ├── RoomEvent.java       (base, with String type discriminator)
│   │       ├── StateUpdatedEvent.java
│   │       ├── PlayersUpdatedEvent.java
│   │       ├── GameResultEvent.java
│   │       └── RoomErrorEvent.java
│   └── exception
│       ├── RoomNotFoundException.java
│       ├── RoomFullException.java
│       ├── NotYourTurnException.java
│       ├── InvalidMoveException.java
│       └── ActionNotAllowedException.java
│
├── game
│   ├── rule
│   │   └── GameRuleEngine.java
│   └── model
│       ├── Board.java
│       ├── Piece.java               (type enum + side enum)
│       ├── PieceType.java           (RAT, CAT, DOG, WOLF, LEOPARD, TIGER, LION, ELEPHANT)
│       ├── Position.java            (row, col — 0-indexed)
│       ├── MoveResult.java          (valid flag + specialEvent)
│       └── WinResult.java           (winner side + reason)
│
├── eve
│   ├── controller
│   │   └── EveAdminController.java
│   └── service
│       └── DockerBotService.java
│
└── lobby
    └── controller
        └── LobbyController.java
```

---

## 3. RSocket Interaction Models

| Route                     | Model              | Controller Method Signature |
|---------------------------|--------------------|-----------------------------|
| `room.create`             | request-response   | `Mono<CreateRoomResponse> create(CreateRoomRequest, RSocketRequester)` |
| `room.{id}.join`          | request-response   | `Mono<JoinRoomResponse> join(@DestinationVariable id, RSocketRequester)` |
| `room.{id}.watch`         | request-response   | `Mono<Void> watch(@DestinationVariable id, RSocketRequester)` |
| `room.{id}.subscribe`     | request-stream     | `Flux<RoomEvent> subscribe(@DestinationVariable id, RSocketRequester)` |
| `room.{id}.move`          | request-response   | `Mono<MoveAckResponse> move(@DestinationVariable id, MoveRequest, RSocketRequester)` |
| `room.{id}.leave`         | fire-and-forget    | `Mono<Void> leave(@DestinationVariable id, RSocketRequester)` |
| `room.{id}.rematch`       | fire-and-forget    | `Mono<Void> rematch(@DestinationVariable id, RSocketRequester)` |
| `lobby.rooms`             | request-stream     | `Flux<LobbySnapshot> lobbyRooms()` |
| `admin.eve.rooms`         | request-stream     | `Flux<EveRoomSnapshot> eveRooms()` |

`RSocketRequester` is injected at `@ConnectMapping` and stored per session. Controller methods receive the session identity via a resolved `sessionId` argument (extracted from the requester or a metadata frame).

---

## 4. Broadcast Model — Reactor Sinks

### Per-room Sink

```
RoomEventBus
  ConcurrentHashMap<String, Sinks.Many<RoomEvent>> sinks

  getSink(roomId)  → creates if absent, returns existing
  emit(roomId, event)  → sinks.get(roomId).tryEmitNext(event)
  destroy(roomId)  → sinks.get(roomId).tryEmitComplete(); sinks.remove(roomId)
```

`Sinks.many().multicast().onBackpressureBuffer()` — all active subscribers receive every event.

### Subscribe route returns the Sink's Flux

```java
@MessageMapping("room.{id}.subscribe")
public Flux<RoomEvent> subscribe(@DestinationVariable String id, ...) {
    // verify caller is in room (player or spectator)
    // if new mid-game spectator: emit current STATE_UPDATED first
    return roomEventBus.getSink(id).asFlux();
}
```

### Error delivery to a single session

`ROOM_ERROR` events cannot use the shared Sink (all subscribers would see them).  
Instead, use a **per-session error Sink**: `Map<sessionId, Sinks.Many<RoomEvent>>` in `RoomEventBus`. The subscribe stream merges the room Sink and the session error Sink using `Flux.merge()`.

```
subscriber stream = Flux.merge(
    roomEventBus.getRoomSink(roomId).asFlux(),
    roomEventBus.getSessionErrorSink(sessionId).asFlux()
)
```

### Global lobby Sink

```
LobbyService
  Sinks.Many<LobbySnapshot> lobbySink = Sinks.many().replay().latest()

  emitSnapshot()  → rebuilds snapshot from Redis, emits to lobbySink
```

`replay().latest()` ensures new lobby subscribers immediately receive the current state without waiting for the next change.

---

## 5. Session Management

### On RSocket connect

```java
@ConnectMapping
public Mono<Void> onConnect(RSocketRequester requester) {
    String sessionId = extractSessionId(requester); // from metadata
    sessionRegistry.put(sessionId, requester);
    requester.rsocket()
        .onClose()
        .doFinally(signal -> handleDisconnect(sessionId))
        .subscribe();
    return Mono.empty();
}
```

### On disconnect

```
handleDisconnect(sessionId):
  1. Look up roomId = Redis.get("session:" + sessionId)
  2. If no roomId → nothing to do
  3. Load RoomState from Redis
  4. If spectator → remove from spectators[], emit PLAYERS_UPDATED
  5. If player and status = PLAYING →
       emit GAME_RESULT(reason = OPPONENT_DISCONNECTED_TIMEOUT)
       set status = ENDED, save to Redis
  6. If player and status = WAITING →
       destroy room (Redis DEL room:{roomId}, destroy Sink)
       emit lobby update (room gone)
  7. Delete session:{sessionId} from Redis
  8. Clean up session error Sink
```

---

## 6. RoomService State Transitions

```
create()
  → validate request
  → generate roomId (UUIDv7)
  → initialize Board (BoardInitializer.standard())
  → build RoomState (status=WAITING, creator=PLAYER_1)
  → save to Redis
  → register session→room in Redis
  → register session error Sink
  → if EVE: DockerBotService.spawnBotPair(roomId)
  → emit lobby snapshot
  → return CreateRoomResponse

join(roomId, sessionId)
  → load RoomState, validate (exists, WAITING, not full)
  → assign PLAYER_2
  → set status = PLAYING
  → save to Redis
  → register session→room in Redis
  → emit PLAYERS_UPDATED via room Sink
  → emit lobby snapshot
  → return JoinRoomResponse

watch(roomId, sessionId)
  → load RoomState, validate (exists, allowSpectator=true, not ENDED)
  → add to spectators[]
  → save to Redis
  → emit PLAYERS_UPDATED via room Sink
  → enqueue STATE_UPDATED to session error Sink (immediate board sync)
  → emit lobby snapshot
  → return Mono.empty()

move(roomId, sessionId, from, to)
  → load RoomState
  → validate: caller is player, correct turn, status=PLAYING
  → GameRuleEngine.validate(board, from, to, side)
  → apply move to board
  → record in history
  → check WinResult via GameRuleEngine.checkWin(board, side)
  → save to Redis
  → emit STATE_UPDATED via room Sink
  → if won: emit GAME_RESULT, set status=ENDED, save, emit lobby snapshot
  → return MoveAckResponse

rematch(roomId, sessionId)
  → validate: mode=PVP, status=ENDED, both sessions still in registry
  → reset board, currentTurn=PLAYER_1, moveNumber=0, status=PLAYING
  → save to Redis
  → emit STATE_UPDATED (reset board) via room Sink
```

---

## 7. GameRuleEngine Interface

Pure Java class — no Spring annotations, no Redis, no IO.

```java
public class GameRuleEngine {

    // Returns MoveResult(valid, specialEvent) — never throws
    public MoveResult validate(String[][] board, int[] from, int[] to, String side) { ... }

    // Returns WinResult(winner, reason) or Optional.empty() if game continues
    public Optional<WinResult> checkWin(String[][] board, String lastMoveSide) { ... }

    // Returns list of valid destination positions for piece at position
    public List<int[]> validMoves(String[][] board, int[] from, String side) { ... }
}
```

Board layout: `board[row][col]` where `board[0]` is PLAYER_1's back rank.  
Piece codes follow `"<P1|P2>_<TYPE>"` convention (e.g., `"P1_ELEPHANT"`).

---

## 8. EVE Docker Trigger

```java
public class DockerBotService {

    // Calls Docker daemon API (via docker-java or ProcessBuilder + docker CLI)
    public void spawnBotPair(String roomId) {
        spawnContainer(roomId, "PLAYER_1");
        spawnContainer(roomId, "PLAYER_2");
    }

    private void spawnContainer(String roomId, String side) {
        // docker run --rm -e ROOM_ID=<id> -e SERVER_URL=<url> -e SIDE=<side> <bot-image>
    }

    // Called by admin.eve.rooms stream for health
    public ContainerHealth inspectContainer(String containerId) { ... }
}
```

Bot image name and server URL are injected from application config (`app.eve.bot-image`, `app.eve.server-url`).

---

## 9. Redis Key Design

| Key                    | Value           | TTL                    |
|------------------------|-----------------|------------------------|
| `room:{roomId}`        | RoomState JSON  | 2h from last activity  |
| `session:{sessionId}`  | roomId String   | same as room           |

Redis operations use `ReactiveRedisTemplate<String, String>` with Jackson serialization.

---

## 10. Lobby Filter Logic

Rooms shown in lobby snapshot:
- `status = WAITING` (any mode — player slot open)
- `status = PLAYING` AND `allowSpectator = true` (watchable)

Rooms hidden:
- `status = ENDED`
- `status = PLAYING` AND `allowSpectator = false`

The lobby snapshot is rebuilt from Redis on every qualifying state change (create, join, watch, leave, game end).

---

## 11. Admin EVE Dashboard

`admin.eve.rooms` stream emits `EveRoomSnapshot` on:
- New EVE room created
- A bot joins (status WAITING → PLAYING)
- Game result emitted
- Container health changes (polled every 10s by `DockerBotService`)

Container health is fetched from Docker daemon's inspect API and merged into the snapshot.

---

## 12. Assumptions & Decisions

| Item | Decision |
|------|----------|
| Auth enforcement | Skipped for now — co-worker's task. Routes are `permitAll()` in current `RSocketConfig`. |
| Bot identification | Bots set a metadata key `isBot=true` on connect. Stored in `PlayerInfo.isBot`. |
| PVE bot join order | Creator = PLAYER_1. Bot connects and calls `room.{id}.join` → becomes PLAYER_2. |
| EVE join order | Both bots call `room.{id}.join`. First to connect = PLAYER_1, second = PLAYER_2. Bots are passed their intended `SIDE` env var but server assigns sequentially — they must handle either side. |
| Rematch board orientation | PLAYER_1 always starts on the same side. No side-swapping on rematch. |
| Bet flag | Stored in `RoomState.allowBet`. No wallet, bet placement, or payout logic implemented now. |
| Docker client | Use `docker-java` library or simple `ProcessBuilder` wrapping `docker run`. Choose based on team preference. |
| Piece coordinates | `[row, col]`, 0-indexed. Row 0 = PLAYER_1 back rank. Consistent between `GameRuleEngine` and any FE rule helper. |
