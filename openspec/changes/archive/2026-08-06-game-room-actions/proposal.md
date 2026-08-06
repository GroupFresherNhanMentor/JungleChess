# Proposal: In-Game Room Actions (RSocket)

## Problem

The backend has no gameplay implementation. RSocket is configured at `/rsocket` and Redis is on the classpath, but there are no handlers for room lifecycle or in-game actions. Players, bots, and spectators currently have no way to create rooms, join games, make moves, or observe matches in real time.

The original architecture doc assumed STOMP over WebSocket. The team has since aligned on RSocket over WebSocket, which changes the broadcast model (no built-in broker — must use Reactor Sinks) and the interaction patterns (request-response, fire-and-forget, request-stream instead of `/app/*` / `/topic/*` destinations).

## Scope

This change covers the entire backend game-room capability:

**In scope**
- Room creation: PVP, PVE, EVE modes
- Player join (2nd human or bot connecting as PLAYER_2)
- Spectator join (any time, including mid-game)
- Per-room event stream (board state, player updates, game results, errors)
- Move handling with GameRuleEngine validation
- Leave room (player and spectator)
- Rematch (PVP only, both players still connected)
- Lobby listing (live stream of joinable/watchable rooms)
- EVE room management: admin REST endpoint + Docker bot container trigger
- Admin EVE dashboard stream (room status + container health)
- Disconnect handling (session cleanup, game result on dropout)
- Redis persistence of RoomState
- GameRuleEngine skeleton (pure logic, no Spring — full rule implementation is a separate concern)

**Out of scope**
- Auth / JWT verification (co-worker's task)
- Frontend (Angular) RSocket client
- Bot AI implementation (co-worker's task — bots connect as normal WS clients)
- Bet mechanics (only `allowBet` flag stored; payout logic deferred)
- Match history persistence
- Full Minimax bot engine

## Proposed Approach

Use RSocket over WebSocket (already wired at `spring.rsocket.server.transport=websocket, mapping-path=/rsocket`).

**State**: `RoomState` stored as JSON in Redis (`room:{roomId}` key). Session→room reverse map also in Redis (`session:{sessionId}` key).

**Broadcast**: One `Sinks.Many<RoomEvent>` per active room, held in a `ConcurrentHashMap` in `RoomEventBus`. A singleton `Sinks.Many<LobbySnapshot>` in `LobbyService` drives the lobby stream.

**Mode-agnostic gameplay**: All three modes (PVP / PVE / EVE) are identical on the server during gameplay — it sees two WS clients making moves. Mode only determines creation flow (EVE → Docker trigger) and forces EVE rooms to `allowSpectator=true, allowBet=true`.

**EVE Docker trigger**: Admin calls `POST /api/admin/room/eve` → server creates RoomState → calls Docker API to start two bot containers with `ROOM_ID`, `SERVER_URL`, and `SIDE` env vars. Containers connect as normal clients, play the game, and exit (`--rm`) on game end.

**Spectators**: Join via a separate `room.{id}.watch` RSocket route. Added to `RoomState.spectators[]`. Receive the same event stream as players. Cannot call `move` or `rematch` (server enforces `ACTION_NOT_ALLOWED`). New mid-game spectators receive current board state immediately on subscription.

**Rematch**: Available only in PVP mode. Server validates that both `players[].sessionId` are still in the active-connection registry before resetting the board.
