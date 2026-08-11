## 1. Backend Chat Data Layer & Redis Integration

- [x] 1.1 Create `ChatMessageRecord` record DTO (`id`, `senderUserId`, `senderName`, `senderSide`, `content`, `timestamp`) and `SendChatRequest` request payload.
- [x] 1.2 Create `ChatMessageEvent` room event class extending `RoomEvent`.
- [x] 1.3 Create `ChatRepository` using `StringRedisTemplate` with pipelined native operations (`RPUSH`, `LTRIM -15 -1`, `EXPIRE 86400`) and `LRANGE 0 -1` fetcher.

## 2. Backend Service & STOMP Controller Logic

- [x] 2.1 Add `sendChatMessage(String roomId, String sessionId, String content)` method to `RoomService` to validate user membership, save to `ChatRepository`, and broadcast `ChatMessageEvent` to `/topic/room/{roomId}`.
- [x] 2.2 Update `RoomJoinedEvent` and `RoomService` to include recent chat history (fetched from `ChatRepository`) in initial join/watch/rejoin events.
- [x] 2.3 Add `@MessageMapping("/room/{id}/chat")` endpoint to `RoomController`.

## 3. Frontend Chat Integration & UI

- [x] 3.1 Update Angular `room-events.models.ts` with `ChatMessageRecord` and `ChatMessageEvent` interfaces.
- [x] 3.2 Add `chatMessages` array to `OnlineGameState` and update `GameRoomService` to handle `CHAT_MESSAGE` events by appending to active local session memory.
- [x] 3.3 Add `sendChatMessage(roomId, content)` method to `GameRoomService`.
- [x] 3.4 Create/update Angular chat UI component with message list, auto-scroll, sender badges (`Player 1`, `Player 2`, `Spectator`), and chat input field.

## 4. Verification & Testing

- [x] 4.1 Create backend unit/integration tests for `ChatRepository` Redis pipeline and `RoomService.sendChatMessage`.
- [x] 4.2 Run end-to-end verification of chat publishing, Redis 15-message trimming, and 24-hour expiration.
