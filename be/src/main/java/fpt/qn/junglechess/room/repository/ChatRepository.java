package fpt.qn.junglechess.room.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.qn.junglechess.room.model.ChatMessageRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

    private static final String CHAT_PREFIX = "chat:room:";
    private static final Duration CHAT_TTL = Duration.ofDays(1);
    private static final int MAX_HISTORY = 15;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void pushMessage(String roomId, ChatMessageRecord message) {
        String key = CHAT_PREFIX + roomId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
                byte[] rawVal = json.getBytes(StandardCharsets.UTF_8);

                connection.listCommands().rPush(rawKey, rawVal);
                connection.listCommands().lTrim(rawKey, -MAX_HISTORY, -1);
                connection.keyCommands().expire(rawKey, CHAT_TTL.toSeconds());
                return null;
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ChatMessageRecord", e);
        }
    }

    public List<ChatMessageRecord> getRecentMessages(String roomId) {
        String key = CHAT_PREFIX + roomId;
        List<String> range = redisTemplate.opsForList().range(key, 0, -1);
        if (range == null || range.isEmpty()) return List.of();

        return range.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ChatMessageRecord.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public void delete(String roomId) {
        redisTemplate.delete(CHAT_PREFIX + roomId);
    }
}
