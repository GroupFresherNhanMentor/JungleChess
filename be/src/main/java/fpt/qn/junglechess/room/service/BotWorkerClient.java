package fpt.qn.junglechess.room.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class BotWorkerClient {

    private final RestTemplate rest = new RestTemplate();

    @Value("${app.bot-worker.url:}")
    private String botWorkerUrl;

    public void assign(String roomId, String side, String difficulty) {
        if (botWorkerUrl == null || botWorkerUrl.isBlank()) {
            log.warn("app.bot-worker.url not configured — skipping bot assignment for room {}", roomId);
            return;
        }
        try {
            rest.postForEntity(
                    botWorkerUrl + "/api/bot/assign",
                    Map.of("roomId", roomId, "side", side, "difficulty", difficulty != null ? difficulty : "MEDIUM"),
                    Void.class);
            log.info("Bot assigned: room={}, side={}, difficulty={}", roomId, side, difficulty);
        } catch (Exception e) {
            log.error("Failed to assign bot to room {} ({}): {}", roomId, side, e.getMessage());
        }
    }
}
