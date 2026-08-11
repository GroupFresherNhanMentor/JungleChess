# Bot Development Guide — Cờ Thú Online (Jungle Chess)

This guide explains everything you need to build a bot that can play in this application. The system is language-agnostic: two reference implementations already exist (Java and Python), and any language that supports WebSockets and JSON can be used.

---

## Table of Contents

1. [How Bots Work — Overview](#1-how-bots-work--overview)
2. [Step 1 — Register a Bot Account](#2-step-1--register-a-bot-account)
3. [Step 2 — Connect via WebSocket STOMP](#3-step-2--connect-via-websocket-stomp)
4. [Step 3 — Listen for Game Invitations](#4-step-3--listen-for-game-invitations)
5. [Step 4 — Join the Room](#5-step-4--join-the-room)
6. [Step 5 — Play the Game](#6-step-5--play-the-game)
7. [Message Reference](#7-message-reference)
8. [Board Representation](#8-board-representation)
9. [Piece Types and Ranks](#9-piece-types-and-ranks)
10. [Configuration and Environment Variables](#10-configuration-and-environment-variables)
11. [Running Your Bot](#11-running-your-bot)
12. [Reference Implementations](#12-reference-implementations)

---

## 1. How Bots Work — Overview

```
Bot process                         Server
    │                                 │
    │── POST /api/auth/bot-register ──▶│  (idempotent, skip if 409)
    │── POST /api/auth/login ─────────▶│
    │◀── { accessToken, refreshToken }─│
    │                                 │
    │── WS connect + STOMP CONNECT ───▶│  (Bearer token in header)
    │── SUBSCRIBE /user/queue/bot-invite│
    │                                 │
    │          (user creates EVE/PVE room, picks your bot)
    │                                 │
    │◀── MESSAGE /user/queue/bot-invite│  { roomId, side, difficulty }
    │── SEND /app/room.{roomId}.bot-join│  { side, difficulty }
    │── SUBSCRIBE /topic/room/{roomId} │
    │                                 │
    │◀── MESSAGE /topic/room/{roomId} ─│  STATE_UPDATED events
    │── SEND /app/room.{roomId}.move ──▶│  { from: [r,c], to: [r,c] }
    │                                 │
    │◀── MESSAGE /topic/room/{roomId} ─│  GAME_RESULT event → disconnect
```

Bots connect to the same WebSocket endpoint as human players. The only difference is the account role: a bot account has the `BOT` role, which causes the server to list it in the bot-selection dropdown when a user creates a PVE or EVE room.

---

## 2. Step 1 — Register a Bot Account

Before connecting, register a bot account. This call is idempotent — call it every startup and ignore a `409 Conflict` response (account already exists).

**Endpoint:** `POST /api/auth/bot-register`

**Request body:**
```json
{
  "username": "my-bot",
  "password": "MyBot@password1",
  "fullName": "My Bot Display Name"
}
```

**Success:** `200 OK` or `201 Created` — proceed to login with this username.

**`409 Conflict` — username is already taken. The bot must stop and report an error:**

```
ERROR: Username 'my-bot' is already taken.
Please set a unique BOT_USERNAME for your bot and restart.
```

Each bot must use a username that no other account (human or bot) has registered. Choose something specific to your bot, e.g. `team-alpha-bot`, `openings-bot-v2`, etc. Do **not** try to continue or silently fall back — a duplicate username means the server cannot route game invitations to the correct bot.

**Then login:**

**Endpoint:** `POST /api/auth/login`

**Request body:**
```json
{
  "username": "my-bot",
  "password": "MyBot@password1"
}
```

**Response:**
```json
{
  "data": {
    "accessToken": "<JWT>",
    "refreshToken": "<refresh JWT>"
  }
}
```

Store both tokens. Use `accessToken` in the STOMP `Authorization` header. Use `refreshToken` to renew when the access token expires (call `POST /api/auth/refresh` with `{ "refreshToken": "..." }`).

---

## 3. Step 2 — Connect via WebSocket STOMP

Connect to the WebSocket endpoint and complete the STOMP handshake.

**WebSocket URL:** `ws://<host>:8080/ws`  
**Protocol:** STOMP 1.1/1.2 over WebSocket

**STOMP CONNECT frame headers:**
```
accept-version:1.1,1.2
heart-beat:10000,10000
Authorization:Bearer <accessToken>
```

Wait for the `CONNECTED` frame from the server before subscribing or sending.

**Reconnection:** Implement exponential backoff reconnection (2^attempt seconds, capped at 60s). On reconnect, refresh the access token first, then re-CONNECT and re-subscribe to all topics.

---

## 4. Step 3 — Listen for Game Invitations

After STOMP is connected, subscribe to the bot invite queue:

```
SUBSCRIBE
id:sub-bot-invite
destination:/user/queue/bot-invite
```

When a user creates a PVE or EVE room and selects your bot, the server pushes an invitation message to this topic:

```json
{
  "roomId": "abc123",
  "side": "PLAYER_1",
  "difficulty": "MEDIUM"
}
```

| Field | Values | Description |
|---|---|---|
| `roomId` | string | The room to join |
| `side` | `PLAYER_1` or `PLAYER_2` | Which side the bot plays |
| `difficulty` | `EASY`, `MEDIUM`, `HARD` | Hint for your search depth |

**Your bot appears in the dropdown only while it is connected.** The server tracks connected bots in real time via their STOMP session. If your bot disconnects, it disappears from the selection list.

---

## 5. Step 4 — Join the Room

When you receive an invitation, join the room immediately:

**Send:**
```
SEND
destination:/app/room.{roomId}.bot-join
content-type:application/json

{"side":"PLAYER_1","difficulty":"MEDIUM"}
```

Note the destination format: `/app/room.{roomId}.bot-join` (dots, not slashes, except for the topic subscription below).

Then subscribe to the room's broadcast topic to receive game events:

```
SUBSCRIBE
id:sub-room-abc123
destination:/topic/room/{roomId}
```

The game does not start until the room creator clicks **Start Game** (for PVP) — for PVE/EVE the creator starts it directly. After start, `STATE_UPDATED` events flow on the room topic.

---

## 6. Step 5 — Play the Game

### Receiving game events

All game events arrive as JSON messages on `/topic/room/{roomId}`. Inspect the `type` field to dispatch:

| `type` | When sent | What to do |
|---|---|---|
| `STATE_UPDATED` | After every move, and after game start | Parse board + turn, compute move if it's your turn |
| `GAME_RESULT` | Game over | Record result, shut down session |
| `PLAYERS_UPDATED` | Player joins/leaves | Informational, no action needed |

### Deciding when to move

Check `currentTurn` against your `side`:

```json
{
  "type": "STATE_UPDATED",
  "status": "PLAYING",
  "currentTurn": "PLAYER_1",
  "board": [ ... ],
  "moveNumber": 5
}
```

If `currentTurn == your_side`, compute a move and send it.

### Sending a move

```
SEND
destination:/app/room.{roomId}.move
content-type:application/json

{"from":[row,col],"to":[row,col]}
```

Coordinates use **0-based row/column** where `(0,0)` is the top-left corner of the board.

**Minimum move delay:** Enforce at least 1200 ms between receiving your turn and sending a move. This prevents instant-move flicker in the UI and is enforced by the reference implementations.

---

## 7. Message Reference

### `STATE_UPDATED` (server → bot)

```json
{
  "type": "STATE_UPDATED",
  "status": "PLAYING",
  "currentTurn": "PLAYER_1",
  "board": [
    ["PLAYER_1_LION", null, null, null, null, null, "PLAYER_1_TIGER"],
    [null, "PLAYER_1_DOG", null, null, null, "PLAYER_1_CAT", null],
    ...
  ],
  "moveNumber": 12,
  "lastMove": {
    "from": [2, 0],
    "to": [3, 0],
    "piece": { "side": "PLAYER_1", "type": "RAT" }
  }
}
```

### `GAME_RESULT` (server → bot)

```json
{
  "type": "GAME_RESULT",
  "winner": "PLAYER_1",
  "reason": "CAPTURED_ALL"
}
```

Possible `reason` values: `CAPTURED_ALL`, `ENTERED_DEN`, `DRAW_REPETITION`, `DRAW_MAX_MOVES`, `OPPONENT_DISCONNECTED_TIMEOUT`.

### Bot join (bot → server)

```json
{ "side": "PLAYER_1", "difficulty": "MEDIUM" }
```

Sent to `/app/room.{roomId}.bot-join`.

### Move (bot → server)

```json
{ "from": [2, 0], "to": [3, 0] }
```

Sent to `/app/room.{roomId}.move`.

---

## 8. Board Representation

The board is a **9×7 grid** (9 rows, 7 columns). It is transmitted as a 2D JSON array `board[row][col]`.

- `null` — empty square
- `"PLAYER_1_LION"` — piece owned by PLAYER_1 of type LION
- `"PLAYER_2_RAT"` — piece owned by PLAYER_2 of type RAT

The cell format is always: `"{SIDE}_{PIECE_TYPE}"`.

### Special squares

| Square | Position | Rule |
|---|---|---|
| PLAYER_1 Den | `(3, 3)` | PLAYER_1 wins if PLAYER_2 piece enters |
| PLAYER_2 Den | `(5, 3)` | PLAYER_2 wins if PLAYER_1 piece enters |
| River (water) | rows 3-5, cols 1-2 and 4-5 | Only RAT can enter; LION and TIGER can jump over |
| Trap squares | surround each den | Piece standing here has effective rank 0 |

**Coordinate reference:**

```
Col:   0   1   2   3   4   5   6
Row 0: L . . . . . T        ← PLAYER_1 home row
Row 1: . D . . . C .
Row 2: R . P . W . E
Row 3: . ~ ~ D ~ ~ .        ← PLAYER_1 den at (3,3); ~ = water
Row 4: . ~ ~ . ~ ~ .
Row 5: . ~ ~ D ~ ~ .        ← PLAYER_2 den at (5,3)
Row 6: R . P . W . E
Row 7: . D . . . C .
Row 8: T . . . . . L        ← PLAYER_2 home row
```

(L=Lion, T=Tiger, D=Dog/Den, C=Cat, R=Rat, P=Leopard, W=Wolf, E=Elephant, ~=water)

---

## 9. Piece Types and Ranks

Higher rank beats lower rank (with the RAT exception).

| Piece | Rank | Special rules |
|---|---|---|
| RAT | 1 | Can enter water; can capture ELEPHANT; cannot be captured by ELEPHANT |
| CAT | 2 | — |
| DOG | 3 | — |
| WOLF | 4 | — |
| LEOPARD | 5 | — |
| TIGER | 6 | Can jump over water rows/columns (blocked if RAT is in the water) |
| LION | 7 | Can jump over water rows/columns (blocked if RAT is in the water) |
| ELEPHANT | 8 | Cannot capture RAT |

A piece on a **trap square** belonging to the opponent has effective rank 0 and can be captured by any piece.

---

## 10. Configuration and Environment Variables

Your bot process should be configurable via environment variables:

| Variable | Default | Description |
|---|---|---|
| `SERVER_URL` | `ws://localhost:8080/ws` | WebSocket URL of the game server |
| `BACKEND_HTTP_URL` | `http://localhost:8080` | HTTP base URL for auth calls |
| `BOT_USERNAME` | *(your choice)* | Bot account username (unique per bot) |
| `BOT_PASSWORD` | *(your choice)* | Bot account password |
| `DIFFICULTY` | `MEDIUM` | Default difficulty if not specified in invite |
| `ROOM_ID` | *(optional)* | Manually join a specific room on startup |
| `SIDE` | `PLAYER_2` | Side to use when `ROOM_ID` is set |

**Important:** Each bot process must use a unique username. If two bots share a username the server will see them as the same user and only one will appear in the dropdown.

---

## 11. Running Your Bot

### Locally (development)

Make sure the backend server is running first (`./mvnw spring-boot:run` in `be/`).

**Java bot (reference):**
```bash
cd bot-worker
./mvnw spring-boot:run
```

**Python bot (reference):**
```bash
cd py-bot-worker
pip install -r requirements.txt
BOT_USERNAME=py-bot-worker BOT_PASSWORD=PyBot@worker1 python main.py
```

### With Docker (custom bot)

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install -r requirements.txt
COPY . .
ENV SERVER_URL=ws://jc-be:8080/ws \
    BACKEND_HTTP_URL=http://jc-be:8080 \
    BOT_USERNAME=my-bot \
    BOT_PASSWORD=MyBot@password1
CMD ["python", "main.py"]
```

Run your container on the same Docker network as the backend (`jc-be` hostname):
```bash
docker build -t my-bot .
docker run --network junglechess_default \
  -e BOT_USERNAME=my-bot \
  -e BOT_PASSWORD=MyBot@password1 \
  my-bot
```

> **Note:** The official `docker-compose.yml` includes the Java bot worker (`jc-bot-worker`). External bots (like the Python bot or your own) are run separately and connect to the same backend — do not add them to `docker-compose.yml`.

### Verifying your bot is online

Once connected, your bot should appear in the bot selection dropdown when a user opens **Create Room** and selects EVE or PVE mode. You can also call:

```
GET /api/bots/online
```

This returns a list of all currently connected bots:
```json
[
  { "id": "user-uuid", "name": "my-bot" }
]
```

---

## 12. Reference Implementations

Two complete reference bots are included in the repository:

### Java bot — `bot-worker/`

A Spring Boot application using the Spring WebSocket STOMP client. Uses an Alpha-Beta search engine with iterative deepening and a transposition table.

Key classes:
- `BotAuthClient` — handles registration, login, token refresh
- `StompConnectionService` — manages STOMP connection and reconnection
- `BotSessionManager` — receives invites, creates per-game sessions
- `BotGameSession` — receives STATE_UPDATED, computes and sends moves
- `AlphaBetaBotEngine` — the search engine (implements `BotEngine` interface)

Default credentials: `bot-worker` / `Bot@worker1`

### Python bot — `py-bot-worker/`

A standalone Python process using `websocket-client` with a manual STOMP frame parser (no STOMP library dependency for the WebSocket layer). Same Alpha-Beta engine implemented in pure Python.

Key modules:
- `botworker/auth.py` — registration and login via `requests`
- `botworker/stomp_client.py` — raw STOMP over WebSocket
- `botworker/session.py` — `BotSessionManager` and `BotGameSession`
- `game/bot.py` — Alpha-Beta engine with iterative deepening

Default credentials: `py-bot-worker` / `PyBot@worker1`

### Minimum viable bot checklist

To build your own bot from scratch:

- [ ] `POST /api/auth/bot-register` on startup; stop with a clear error if 409 (username already taken — developer must configure a unique `BOT_USERNAME`)
- [ ] `POST /api/auth/login` → store access + refresh tokens
- [ ] WebSocket connect to `/ws`
- [ ] STOMP `CONNECT` with `Authorization: Bearer <token>`
- [ ] `SUBSCRIBE /user/queue/bot-invite`
- [ ] On invite: `SEND /app/room.{roomId}.bot-join` + `SUBSCRIBE /topic/room/{roomId}`
- [ ] On `STATE_UPDATED` where `currentTurn == mySide`: compute move, wait ≥ 1200 ms, `SEND /app/room.{roomId}.move`
- [ ] On `GAME_RESULT`: clean up session
- [ ] Reconnect with exponential backoff on disconnect
- [ ] Token refresh on reconnect (use refresh token before falling back to full re-login)
