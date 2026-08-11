## Context

Refer to `proposal.md` for motivation. Currently, the Spring Boot backend (`be/`) uses `StringRedisTemplate` for room state persistence. This design introduces a dedicated Redis List per room for chat messages to keep chat operations isolated from core game state serialization.

## Goals / Non-Goals

**Goals:**
- Provide a unified, high-performance in-room chat channel for both players and spectators.
- Store up to 15 recent messages per room using Redis native list commands (`RPUSH` + `LTRIM`) executed in an atomic pipeline.
- Automatically expire chat room keys after 24 hours (`EXPIRE`) to prevent orphaned storage without needing background cron jobs.
- Deliver the recent 15 messages to clients when joining, rejoining, or syncing room state.
- Allow connected frontend clients to maintain an uncapped local chat log during their active session.

**Non-Goals:**
- Cross-room or lobby-wide chat system.
- Persistent database (PostgreSQL) archive of game chat logs.
- Direct private messaging between players.

## Decisions

### Decision 1: Dedicated Redis Native Capped List (`room:chat:{roomId}`) over `RoomState` Blob Embedding
- **Choice**: Store chat history in Redis List under `room:chat:{roomId}` using pipelined `RPUSH`, `LTRIM -15 -1`, and `EXPIRE 86400`.
- **Rationale**:
  - `LTRIM -15 -1` executes in $O(1)$ time in Redis. If count $\le 15$, `LTRIM` is a native no-op.
  - Decouples chat writes from `RoomState` JSON serialization, preventing unnecessary CPU overhead during move calculations.
  - Native Redis key expiration (`EXPIRE 86400`) handles 24-hour cleanup automatically.
- **Alternatives Considered**:
  - *Embedding chat list inside `RoomState` JSON*: Would require re-serializing the entire room state on every chat message, degrading move processing performance.
  - *Redis Sorted Set (ZSET)*: Overkill since chat messages are already strictly ordered in time.

### Decision 2: STOMP Messaging Routes & Payload Design
- **Inbound Endpoint**: `@MessageMapping("/room/{id}/chat")` accepting `SendChatRequest(String content)`.
- **Outbound Realtime Event**: `ChatMessageEvent` broadcast to `/topic/room/{roomId}` containing:
  ```json
  {
    "type": "CHAT_MESSAGE",
    "roomId": "room-123",
    "message": {
      "id": "uuid",
      "senderUserId": "usr-123",
      "senderName": "Player 1",
      "senderSide": "PLAYER_1",
      "content": "Hello!",
      "timestamp": 1723356000000
    }
  }
  ```
- **Sync Payload**: Add `recentChat: List<ChatMessageRecord>` to `RoomJoinedEvent` and `RoomCreatedEvent`.

### Decision 3: Frontend Uncapped Session Buffer
- **Choice**: Initialize FE chat state from `recentChat` in `ROOM_JOINED`, then append all incoming `CHAT_MESSAGE` events without truncating.
- **Rationale**: Gives active users a full history of their live session while keeping backend memory strictly bounded.

## Risks / Trade-offs

- **[Risk] Chat Spamming**: User floods the room with high-frequency messages.
  - *Mitigation*: Validate content length ($\le 500$ chars), trim whitespace, and reject blank messages server-side.
- **[Risk] Room Termination Cleanup**: Room deleted before 24-hour TTL expires.
  - *Mitigation*: Call `redisTemplate.delete("room:chat:" + roomId)` during room termination, while relying on 24-hour TTL as a fallback.
