package fpt.qn.junglechess.room.controller;

import fpt.qn.junglechess.common.exception.AppException;
import fpt.qn.junglechess.room.dto.event.RoomErrorEvent;
import fpt.qn.junglechess.room.dto.request.BotJoinRequest;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.request.MoveRequest;
import fpt.qn.junglechess.room.service.DisconnectScheduler;
import fpt.qn.junglechess.room.service.RoomEventBus;
import fpt.qn.junglechess.room.service.RoomService;
import fpt.qn.junglechess.room.service.SessionRegistry;
import fpt.qn.junglechess.security.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomEventBus eventBus;
    private final SessionRegistry sessionRegistry;
    private final DisconnectScheduler disconnectScheduler;

    // ── Connection lifecycle ──────────────────────────────────────────────────

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        JwtUserPrincipal principal = extractPrincipal(sha);
        if (principal == null) return;

        String userId = principal.userId().toString();
        boolean isReconnect = disconnectScheduler.cancel(userId);
        if (isReconnect) {
            String oldSessionId = sessionRegistry.getSessionIdForUser(userId);
            if (oldSessionId != null) {
                eventBus.destroySession(oldSessionId);
                sessionRegistry.deregister(oldSessionId);
                log.debug("Evicted stale session {} on reconnect for user {}", oldSessionId, userId);
            }
        }

        sessionRegistry.register(sessionId, principal);
        log.debug("STOMP connected: session={}, user={}", sessionId, principal.username());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        log.debug("STOMP disconnected: session={}", sessionId);
        roomService.handleDisconnect(sessionId);
    }

    // ── Room actions ──────────────────────────────────────────────────────────

    @MessageMapping("room.create")
    public void create(@Payload CreateRoomRequest req, SimpMessageHeaderAccessor sha) {
        String sessionId = sha.getSessionId();
        JwtUserPrincipal principal = extractPrincipal(sha);
        roomService.createRoom(req, sessionId, principal.userId().toString());
    }

    @MessageMapping("room.{id}.join")
    public void join(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        JwtUserPrincipal principal = extractPrincipal(sha);
        roomService.joinRoom(id, sha.getSessionId(), principal.userId().toString());
    }

    @MessageMapping("room.{id}.rejoin")
    public void rejoin(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        JwtUserPrincipal principal = extractPrincipal(sha);
        roomService.rejoinRoom(id, sha.getSessionId(), principal.userId().toString());
    }

    @MessageMapping("room.{id}.watch")
    public void watch(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        JwtUserPrincipal principal = extractPrincipal(sha);
        roomService.watchRoom(id, sha.getSessionId(), principal.userId().toString());
    }

    @MessageMapping("room.{id}.start")
    public void start(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        roomService.startGame(id, sha.getSessionId());
    }

    @MessageMapping("room.{id}.move")
    public void move(@DestinationVariable String id, @Payload MoveRequest req, SimpMessageHeaderAccessor sha) {
        roomService.move(id, sha.getSessionId(), req);
    }

    @MessageMapping("room.{id}.undo")
    public void undo(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        roomService.undoMove(id, sha.getSessionId());
    }

    @MessageMapping("room.{id}.leave")
    public void leave(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        roomService.leaveRoom(id, sha.getSessionId());
    }

    @MessageMapping("room.{id}.rematch")
    public void rematch(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        roomService.rematch(id, sha.getSessionId());
    }

    @MessageMapping("room.{id}.sync")
    public void sync(@DestinationVariable String id, SimpMessageHeaderAccessor sha) {
        roomService.syncRoom(id, sha.getSessionId());
    }

    // ── Bot-worker endpoints (slash-notation used by bot-worker service) ───────

    @MessageMapping("/room/{id}/bot-join")
    public void botJoin(@DestinationVariable String id,
                        @Payload BotJoinRequest req,
                        SimpMessageHeaderAccessor sha) {
        JwtUserPrincipal principal = extractPrincipal(sha);
        roomService.joinRoomAsBot(id, sha.getSessionId(), req.getSide(), principal.userId().toString());
    }

    @MessageMapping("/room/{id}/move")
    public void botMove(@DestinationVariable String id,
                        @Payload MoveRequest req,
                        SimpMessageHeaderAccessor sha) {
        roomService.move(id, sha.getSessionId(), req);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @MessageExceptionHandler(AppException.class)
    public void handleAppException(AppException ex, SimpMessageHeaderAccessor sha) {
        String sessionId = sha.getSessionId();
        log.warn("[STOMP] app exception for session {}: {}", sessionId, ex.getMessage());
        eventBus.emitToSession(sessionId, RoomErrorEvent.of("ACTION_ERROR", ex.getMessage()));
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception ex, SimpMessageHeaderAccessor sha) {
        String sessionId = sha.getSessionId();
        log.error("[STOMP] unexpected error for session {}", sessionId, ex);
        eventBus.emitToSession(sessionId, RoomErrorEvent.of("SERVER_ERROR", "An unexpected error occurred"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JwtUserPrincipal extractPrincipal(StompHeaderAccessor sha) {
        var user = sha.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p;
        }
        return null;
    }

    private JwtUserPrincipal extractPrincipal(SimpMessageHeaderAccessor sha) {
        var user = sha.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p;
        }
        return null;
    }
}
