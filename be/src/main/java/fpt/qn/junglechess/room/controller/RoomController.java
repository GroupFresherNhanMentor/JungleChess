package fpt.qn.junglechess.room.controller;

import fpt.qn.junglechess.common.exception.AppException;
import fpt.qn.junglechess.room.dto.event.RoomErrorEvent;
import fpt.qn.junglechess.room.dto.event.RoomEvent;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.request.MoveRequest;
import fpt.qn.junglechess.room.dto.response.CreateRoomResponse;
import fpt.qn.junglechess.room.dto.response.JoinRoomResponse;
import fpt.qn.junglechess.room.dto.response.MoveAckResponse;
import fpt.qn.junglechess.room.service.RoomEventBus;
import fpt.qn.junglechess.room.service.RoomService;
import fpt.qn.junglechess.room.service.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomEventBus eventBus;
    private final SessionRegistry sessionRegistry;

    // ── Connection lifecycle ──────────────────────────────────────────────────

    @ConnectMapping
    public Mono<Void> onConnect(RSocketRequester requester) {
        String sessionId = UUID.randomUUID().toString();
        // Attach sessionId to the requester for downstream use
        requester.rsocket()
                .metadataPush(null) // metadata-based sessionId injection handled by auth layer later
                .onErrorComplete()
                .subscribe();

        sessionRegistry.register(sessionId, requester);
        requester.rsocket()
                .onClose()
                .doFinally(signal -> {
                    log.debug("Session {} disconnected: {}", sessionId, signal);
                    roomService.handleDisconnect(sessionId).subscribe();
                })
                .subscribe();

        log.debug("New RSocket session: {}", sessionId);
        return Mono.empty();
    }

    // ── Room actions ──────────────────────────────────────────────────────────

    @MessageMapping("room.create")
    public Mono<CreateRoomResponse> create(CreateRoomRequest req, RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.createRoom(req, sessionId, sessionId)
                .onErrorResume(AppException.class, e ->
                        Mono.fromRunnable(() -> emitError(sessionId, e.getMessage(), null))
                                .then(Mono.error(e)));
    }

    @MessageMapping("room.{id}.join")
    public Mono<JoinRoomResponse> join(@DestinationVariable String id,
                                       RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        // isBot flag: bots will set metadata; default false until auth layer handles it
        return roomService.joinRoom(id, sessionId, false, sessionId)
                .onErrorResume(AppException.class, e -> {
                    emitError(sessionId, e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.watch")
    public Mono<Void> watch(@DestinationVariable String id, RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.watchRoom(id, sessionId, sessionId)
                .onErrorResume(AppException.class, e -> {
                    emitError(sessionId, e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.subscribe")
    public Flux<RoomEvent> subscribe(@DestinationVariable String id, RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.subscribeRoom(id, sessionId)
                .onErrorResume(AppException.class, e -> {
                    emitError(sessionId, e.getMessage(), id);
                    return Flux.error(e);
                });
    }

    @MessageMapping("room.{id}.move")
    public Mono<MoveAckResponse> move(@DestinationVariable String id,
                                      MoveRequest req,
                                      RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.move(id, sessionId, req)
                .onErrorResume(AppException.class, e -> {
                    emitError(sessionId, e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.leave")
    public Mono<Void> leave(@DestinationVariable String id, RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.leaveRoom(id, sessionId);
    }

    @MessageMapping("room.{id}.rematch")
    public Mono<Void> rematch(@DestinationVariable String id, RSocketRequester requester) {
        String sessionId = resolveSessionId(requester);
        return roomService.rematch(id, sessionId)
                .onErrorResume(AppException.class, e -> {
                    emitError(sessionId, e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveSessionId(RSocketRequester requester) {
        // Until auth layer injects userId from JWT, use requester identity as sessionId.
        // Auth co-worker will replace this with principal extraction.
        return String.valueOf(System.identityHashCode(requester));
    }

    private void emitError(String sessionId, String message, Object context) {
        eventBus.emitToSession(sessionId, RoomErrorEvent.of("ACTION_ERROR", message));
    }
}
