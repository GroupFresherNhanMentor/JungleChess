# Spec: Game Room (Delta)

## Overview

This spec defines the behavioral requirements for the game-room capability: room lifecycle, in-game actions, spectating, lobby, and EVE admin management. It is a delta spec — it defines what is new or changed relative to the existing system (auth and user management are already in place).

---

## 1. Room Modes

| Mode | Creator        | Player 1      | Player 2      | allowSpectator | allowBet     |
|------|----------------|---------------|---------------|----------------|--------------|
| PVP  | any auth user  | room creator  | 2nd WS client | configurable   | configurable*|
| PVE  | any auth user  | room creator  | bot WS client | configurable   | configurable*|
| EVE  | admin only     | bot container | bot container | forced true    | forced true  |

*`allowBet` is only available when `allowSpectator=true`. Bet mechanics are deferred; only the flag is stored.

---

## 2. RSocket Routes

### 2.1 `room.create` — request-response

**Access**: any authenticated user (PVP/PVE); admin only (EVE)

**Request payload**:
```json
{
  "mode": "PVP | PVE | EVE",
  "allowSpectator": true,
  "allowBet": false
}
```

**Response** (to caller only):
```json
{
  "roomId": "room-8f21",
  "mode": "PVP",
  "status": "WAITING",
  "yourSide": "PLAYER_1",
  "allowSpectator": true,
  "allowBet": false
}
```

**Rules**:
- If `mode = EVE`: server creates RoomState then triggers two Docker bot containers. `allowSpectator` and `allowBet` are forced `true` regardless of payload.
- If `allowBet = true` and `allowSpectator = false`: server rejects with `VALIDATION_ERROR`.
- Creator is auto-assigned `PLAYER_1`.

---

### 2.2 `room.{id}.join` — request-response

**Access**: any authenticated user; bots use the same route

**Request payload**: `{}`

**Broadcast** to `/room.{id}.subscribe` subscribers (`PLAYERS_UPDATED` event):
```json
{
  "type": "PLAYERS_UPDATED",
  "roomId": "room-8f21",
  "players": [
    { "sessionId": "s1", "side": "PLAYER_1", "isBot": false, "userId": "u-001" },
    { "sessionId": "s2", "side": "PLAYER_2", "isBot": true,  "userId": "bot-abc" }
  ],
  "spectators": [],
  "status": "PLAYING"
}
```

**Response** (to caller):
```json
{ "yourSide": "PLAYER_2", "status": "PLAYING" }
```

**Rules**:
- Room must exist and have `status = WAITING`.
- Room must have exactly 1 player (PLAYER_1 already joined).
- Joining sets `status = PLAYING` and emits `PLAYERS_UPDATED`.
- On `PLAYING`, emits lobby update (room removed from WAITING list, may appear as watchable if `allowSpectator = true`).

**Errors**: `ROOM_NOT_FOUND`, `ROOM_FULL`, `GAME_ALREADY_STARTED`

---

### 2.3 `room.{id}.watch` — request-response

**Access**: any authenticated user

**Request payload**: `{}`

**Rules**:
- Room must exist.
- Room `allowSpectator` must be `true`.
- Room `status` must not be `ENDED`.
- Server adds caller to `spectators[]`.
- Server immediately sends current board state (`STATE_UPDATED` event) to the new spectator's stream — so mid-game joiners are in sync.
- Broadcasts `PLAYERS_UPDATED` to all room subscribers.

**Errors**: `ROOM_NOT_FOUND`, `ACTION_NOT_ALLOWED` (spectators disabled or game ended)

---

### 2.4 `room.{id}.subscribe` — request-stream

**Access**: players and spectators already registered in the room

**Returns**: `Flux<RoomEvent>` — live stream of all events for this room until the stream is cancelled or the game ends.

**Rules**:
- Server must verify caller is registered as a player or spectator in the room before emitting.
- Stream completes naturally after a `GAME_RESULT` event is emitted and the room transitions to `ENDED`.

---

### 2.5 `room.{id}.move` — request-response

**Access**: PLAYER_1 or PLAYER_2 only

**Request payload**:
```json
{ "from": [3, 2], "to": [4, 2] }
```

**Response** (to caller): `{ "accepted": true }` on success, or error.

**Broadcast** (`STATE_UPDATED` event to all room subscribers):
```json
{
  "type": "STATE_UPDATED",
  "roomId": "room-8f21",
  "board": [["P1_ELEPHANT", null, ...], ...],
  "currentTurn": "PLAYER_2",
  "lastMove": {
    "from": [3, 2],
    "to": [4, 2],
    "movedPiece": "P1_TIGER",
    "capturedPiece": null,
    "specialEvent": null
  },
  "status": "PLAYING",
  "moveNumber": 13
}
```

`specialEvent` values: `RIVER_JUMP` | `TRAP_NEUTRALIZED` | `null`

**Rules**:
- Caller must be a registered player (not spectator).
- `from` piece must belong to caller's side.
- It must be caller's turn (`currentTurn` matches their side).
- Move validated by `GameRuleEngine.validate(board, from, to, side)`.
- On valid move: update board in Redis, increment `moveNumber`, check win condition.
- On win: emit `STATE_UPDATED` followed immediately by `GAME_RESULT`.
- Board coordinates: `[row, col]`, 0-indexed, 9 rows × 7 cols.

**Errors** (sent as `ROOM_ERROR` event on the offending session's stream):
`NOT_YOUR_TURN`, `INVALID_MOVE`, `ACTION_NOT_ALLOWED`, `GAME_ALREADY_ENDED`

---

### 2.6 `room.{id}.leave` — fire-and-forget

**Access**: players and spectators

**Rules**:
- If caller is a spectator: remove from `spectators[]`, broadcast `PLAYERS_UPDATED`.
- If caller is a player and game is `PLAYING`: emit `GAME_RESULT` with `reason = OPPONENT_DISCONNECTED_TIMEOUT`, set `status = ENDED`.
- If caller is a player and game is `WAITING` (only PLAYER_1 present): destroy room, remove from Redis, remove Sink.
- If caller is a player and game is `ENDED`: clean up session mapping.

---

### 2.7 `room.{id}.rematch` — fire-and-forget

**Access**: PLAYER_1 or PLAYER_2

**Rules**:
- Only valid for `mode = PVP`.
- Room `status` must be `ENDED`.
- Both `players[].sessionId` must still be in the active-connection registry.
- Resets board to initial state, sets `currentTurn = PLAYER_1`, `status = PLAYING`, `moveNumber = 0`, `lastMove = null`.
- Broadcasts `STATE_UPDATED` with reset board.

**Error**: `ACTION_NOT_ALLOWED` (mode is not PVP, game not ended, or opponent disconnected)

---

### 2.8 `lobby.rooms` — request-stream

**Access**: anyone

**Returns**: `Flux<LobbySnapshot>` pushed on every room list change.

**LobbySnapshot**:
```json
{
  "rooms": [
    {
      "roomId": "room-8f21",
      "mode": "PVP",
      "status": "WAITING",
      "allowSpectator": true,
      "allowBet": false,
      "playerCount": 1,
      "spectatorCount": 0,
      "createdAt": "2026-08-06T10:00:00Z"
    }
  ]
}
```

**Filter**: include rooms where `status = WAITING`, OR `status = PLAYING` AND `allowSpectator = true`. Exclude `ENDED` rooms.

---

### 2.9 `admin.eve.rooms` — request-stream

**Access**: admin only

**Returns**: `Flux<EveRoomSnapshot>` — live view of active EVE rooms.

```json
{
  "rooms": [
    {
      "roomId": "room-eve-01",
      "status": "PLAYING",
      "moveNumber": 42,
      "spectatorCount": 7,
      "bot1": { "containerId": "abc123", "side": "PLAYER_1", "health": "RUNNING" },
      "bot2": { "containerId": "def456", "side": "PLAYER_2", "health": "RUNNING" },
      "startedAt": "2026-08-06T09:00:00Z"
    }
  ]
}
```

`health` values: `RUNNING` | `EXITED` | `ERROR` | `UNKNOWN`

---

## 3. EVE Room Creation (REST)

**Endpoint**: `POST /api/admin/room/eve`  
**Access**: admin role only

**Response** `201 Created`:
```json
{ "roomId": "room-eve-01", "status": "WAITING" }
```

**Server behavior**:
1. Create `RoomState` in Redis (`status = WAITING`, `allowSpectator = true`, `allowBet = true`).
2. Call `DockerBotService.spawnBotPair(roomId)` — starts two containers:
   - Container 1: `-e SIDE=PLAYER_1 -e ROOM_ID=<id> -e SERVER_URL=<url> --rm`
   - Container 2: `-e SIDE=PLAYER_2 -e ROOM_ID=<id> -e SERVER_URL=<url> --rm`
3. Return `roomId` to admin.
4. Bots connect via RSocket, call `room.{id}.join` each, game proceeds normally.
5. On `GAME_RESULT`, bots disconnect and containers exit automatically (`--rm`).

---

## 4. RoomEvent Types

All events share a `type` discriminator field.

| Type              | Triggered by                                | Recipients         |
|-------------------|---------------------------------------------|--------------------|
| `STATE_UPDATED`   | valid move, rematch reset                   | all room subscribers |
| `PLAYERS_UPDATED` | join, watch, leave                          | all room subscribers |
| `GAME_RESULT`     | win condition, leave during game, disconnect| all room subscribers |
| `ROOM_ERROR`      | invalid action                              | offending session only |

---

## 5. RoomState Data Model

```
RoomState (stored as JSON in Redis key: room:{roomId})
  roomId        String
  mode          PVP | PVE | EVE
  status        WAITING | PLAYING | ENDED
  allowSpectator Boolean
  allowBet      Boolean
  board         String[9][7]   — pieceCode or null per cell
  currentTurn   PLAYER_1 | PLAYER_2
  moveNumber    int
  players       PlayerInfo[]   — max 2
  spectators    SpectatorInfo[]
  history       MoveRecord[]
  createdAt     Instant

PlayerInfo
  sessionId     String
  side          PLAYER_1 | PLAYER_2
  isBot         Boolean
  userId        String

SpectatorInfo
  sessionId     String
  userId        String

MoveRecord
  from          int[2]
  to            int[2]
  movedPiece    String
  capturedPiece String | null
  specialEvent  String | null
```

Session reverse map (Redis key: `session:{sessionId}`) → `roomId`, TTL matched to room.

---

## 6. Error Codes

| Code                    | Context                                      |
|-------------------------|----------------------------------------------|
| `ROOM_NOT_FOUND`        | roomId does not exist                        |
| `ROOM_FULL`             | 2 players already registered                 |
| `GAME_ALREADY_STARTED`  | join attempted on PLAYING room               |
| `GAME_ALREADY_ENDED`    | action on ENDED room                         |
| `NOT_YOUR_TURN`         | move sent out of turn                        |
| `INVALID_MOVE`          | move fails GameRuleEngine validation         |
| `ACTION_NOT_ALLOWED`    | spectator attempts move; rematch in EVE/PVE; |
|                         | rematch when opponent disconnected           |
| `VALIDATION_ERROR`      | malformed payload (allowBet without spectator)|

---

## 7. Win Conditions

Determined by `GameRuleEngine` after each move:

| Reason                        | Description                                        |
|-------------------------------|----------------------------------------------------|
| `DEN_REACHED`                 | A piece enters the opponent's den                  |
| `NO_VALID_MOVE`               | Current player has no legal moves available        |
| `OPPONENT_DISCONNECTED_TIMEOUT` | Opponent left or disconnected during PLAYING     |
