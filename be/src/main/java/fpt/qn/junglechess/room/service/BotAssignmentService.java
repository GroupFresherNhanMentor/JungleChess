package fpt.qn.junglechess.room.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class BotAssignmentService {

    private final SimpMessagingTemplate messaging;

    @Value("${app.bot-worker.username:bot-worker}")
    private String botWorkerUsername;

    public BotAssignmentService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void assign(String roomId, String side, String difficulty) {
        try {
            messaging.convertAndSendToUser(
                    botWorkerUsername,
                    "/queue/assign",
                    Map.of("roomId", roomId, "side", side, "difficulty", difficulty != null ? difficulty : "MEDIUM")
            );
            log.info("Bot assignment sent via WebSocket: room={}, side={}, difficulty={}", roomId, side, difficulty);
        } catch (Exception e) {
            log.error("Failed to send bot assignment for room {} ({}): {}", roomId, side, e.getMessage());
        }
    }
}
