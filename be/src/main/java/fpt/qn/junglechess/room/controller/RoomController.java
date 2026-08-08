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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import fpt.qn.junglechess.room.service.RSocketSessionContext;
import fpt.qn.junglechess.security.JwtUserPrincipal;

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
    public Mono<Void> onConnect(RSocketRequester requester, @AuthenticationPrincipal JwtUserPrincipal principal) {
        String sessionId = UUID.randomUUID().toString();

        sessionRegistry.register(sessionId, requester, principal);
        requester.rsocket()
                .onClose()
                .doFinally(signal -> {
                    log.debug("Session {} disconnected: {}", sessionId, signal);
                    roomService.handleDisconnect(sessionId).subscribe();
                })
                .subscribe();

        log.debug("New RSocket session {} for user {}", sessionId, principal.userId());
        return Mono.empty();
    }

    // ── Room actions ──────────────────────────────────────────────────────────

    @MessageMapping("room.create")
    public Mono<CreateRoomResponse> create(CreateRoomRequest req, RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.createRoom(req, context.sessionId(), context.principal().userId().toString())
                .onErrorResume(AppException.class, e ->
                        Mono.fromRunnable(() -> emitError(context.sessionId(), e.getMessage(), null))
                                .then(Mono.error(e)));
    }

    @MessageMapping("room.{id}.join")
    public Mono<JoinRoomResponse> join(@DestinationVariable String id,
                                       RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.joinRoom(id, context.sessionId(), false, context.principal().userId().toString())
                .onErrorResume(AppException.class, e -> {
                    emitError(context.sessionId(), e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.watch")
    public Mono<Void> watch(@DestinationVariable String id, RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.watchRoom(id, context.sessionId(), context.principal().userId().toString())
                .onErrorResume(AppException.class, e -> {
                    emitError(context.sessionId(), e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.subscribe")
    public Flux<RoomEvent> subscribe(@DestinationVariable String id, RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.subscribeRoom(id, context.sessionId())
                .onErrorResume(AppException.class, e -> {
                    emitError(context.sessionId(), e.getMessage(), id);
                    return Flux.error(e);
                });
    }

    @MessageMapping("room.{id}.move")
    public Mono<MoveAckResponse> move(@DestinationVariable String id,
                                      MoveRequest req,
                                      RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.move(id, context.sessionId(), req)
                .onErrorResume(AppException.class, e -> {
                    emitError(context.sessionId(), e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    @MessageMapping("room.{id}.leave")
    public Mono<Void> leave(@DestinationVariable String id, RSocketRequester requester) {
        return roomService.leaveRoom(id, resolveSessionContext(requester).sessionId());
    }

    @MessageMapping("room.{id}.rematch")
    public Mono<Void> rematch(@DestinationVariable String id, RSocketRequester requester) {
        RSocketSessionContext context = resolveSessionContext(requester);
        return roomService.rematch(id, context.sessionId())
                .onErrorResume(AppException.class, e -> {
                    emitError(context.sessionId(), e.getMessage(), id);
                    return Mono.error(e);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RSocketSessionContext resolveSessionContext(RSocketRequester requester) {
        RSocketSessionContext context = sessionRegistry.findByRequester(requester);
        if (context == null) {
            throw new AccessDeniedException("RSocket session is not authenticated");
        }
        return context;
    }

    private void emitError(String sessionId, String message, Object context) {
        eventBus.emitToSession(sessionId, RoomErrorEvent.of("ACTION_ERROR", message));
    }
}
