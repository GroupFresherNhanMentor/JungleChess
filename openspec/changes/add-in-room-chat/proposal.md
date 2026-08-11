## Why

Players and spectators in a Jungle Chess game currently have no in-game communication channel. Adding an in-room real-time chat feature enhances social interaction and spectator engagement without compromising server performance or bloating game state payloads.

## What Changes

- Add a unified in-room chat channel accessible to both players (`PLAYER_1`, `PLAYER_2`) and spectators.
- Provide a STOMP message endpoint for sending chat messages to the room.
- Implement high-performance Redis native storage (`room:chat:{roomId}`) maintaining a capped list of the **most recent 15 messages** (`LTRIM`) with an automatic **24-hour expiration TTL** (`EXPIRE`).
- Include the recent chat history (up to 15 messages) when clients join, rejoin, or sync room state.
- Enable frontend clients to maintain an unlimited in-memory chat log during an active session while connected.

## Capabilities

### New Capabilities
- `room-chat`: Unified in-room real-time chat service for players and spectators with Redis native 15-message cap and 24-hour TTL.

### Modified Capabilities

*(None)*

## Impact

- **Backend (`be/`)**:
  - `RoomController`: New STOMP message mapping `@MessageMapping("/room/{id}/chat")`.
  - `ChatRepository`: New Redis repository utilizing pipelined `RPUSH`, `LTRIM`, and `EXPIRE` on `room:chat:{roomId}`.
  - `RoomService`: Integrates recent chat retrieval into `RoomJoinedEvent` payloads.
  - `RoomJoinedEvent`: Field `recentChat` added.
- **Frontend (`fe/`)**:
  - `GameRoomService`: Added chat subscription and send method `sendChatMessage(roomId, content)`.
  - UI Component: Added in-room chat panel supporting message display, role indicators, and autoscroll.
- **Dependencies**: No external library additions; uses existing Spring Data Redis and `@stomp/stompjs`.
