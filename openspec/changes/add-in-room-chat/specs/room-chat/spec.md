## Purpose

Provides a unified real-time chat channel for players and spectators in a game room with Redis native list retention (up to 15 messages) and automatic 24-hour expiration.

## ADDED Requirements

### Requirement: Unified Chat Endpoint for Players and Spectators
The system SHALL provide a STOMP endpoint `/app/room.{id}.chat` allowing any authenticated player (`PLAYER_1`, `PLAYER_2`) or spectator to send text messages to all participants in the room via `/topic/room/{roomId}`.

#### Scenario: Participant sends a chat message
- **WHEN** an authenticated player or spectator sends a message to `/app/room.{roomId}.chat`
- **THEN** the system SHALL construct a `CHAT_MESSAGE` event with `id`, `senderUserId`, `senderName`, `senderSide`, `content`, and `timestamp`, and broadcast it to `/topic/room/{roomId}`.

#### Scenario: Unauthorized or unauthenticated chat attempt
- **WHEN** an unauthenticated client or a client not in the room sends a message to `/app/room.{roomId}.chat`
- **THEN** the system SHALL reject the message and emit an error event to the client's session.

### Requirement: Redis Native List Capped History & 24-Hour Expiration
The system SHALL persist chat messages in a dedicated Redis List `room:chat:{roomId}` using pipelined `RPUSH`, `LTRIM` (retaining the 15 most recent messages), and `EXPIRE` (setting a 24-hour Time-To-Live).

#### Scenario: Storage of chat message in Redis List
- **WHEN** a new chat message is accepted by the server
- **THEN** the server SHALL atomically `RPUSH` the message to `room:chat:{roomId}`, `LTRIM` the list to retain at most 15 entries (`-15` to `-1`), and reset the key expiration to 86,400 seconds (24 hours).

#### Scenario: Catch-up sync on joining a room
- **WHEN** a client joins, watches, or syncs a room (`ROOM_JOINED`, `ROOM_CREATED`, `SYNC`)
- **THEN** the server SHALL fetch the existing messages from Redis List `room:chat:{roomId}` using `LRANGE 0 -1` (up to 15 messages under 24 hours old) and include them in the response payload.

### Requirement: Unlimited Active Client In-Memory Chat Buffer
The frontend client SHALL store all incoming chat messages received over `/topic/room/{roomId}` during an active room session without imposing a 15-message cap.

#### Scenario: Client accumulates live room chat
- **WHEN** a client is connected to `/topic/room/{roomId}` and receives multiple `CHAT_MESSAGE` events
- **THEN** the client SHALL append each message to its local in-memory chat list without truncating older messages while the session remains active.
