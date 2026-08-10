package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.room.dto.event.RoomEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomEventBus {

    private final SimpMessagingTemplate messaging;
    private final SessionRegistry sessionRegistry;

    public void emit(String roomId, RoomEvent event) {
        messaging.convertAndSend("/topic/room/" + roomId, event);
    }

    public void emitToSession(String sessionId, RoomEvent event) {
        String username = sessionRegistry.getUsername(sessionId);
        if (username != null) {
            messaging.convertAndSendToUser(username, "/queue/events", event);
        }
    }

    public void destroyRoom(String roomId) {
        // no-op — broker handles subscription cleanup when clients disconnect
    }

    public void destroySession(String sessionId) {
        // no-op — broker handles subscription cleanup when clients disconnect
    }
}
