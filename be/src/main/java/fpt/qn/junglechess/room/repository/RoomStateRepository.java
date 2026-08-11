package fpt.qn.junglechess.room.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.qn.junglechess.room.model.RoomState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RoomStateRepository {

    private static final String ROOM_PREFIX    = "room:";
    private static final String SESSION_PREFIX = "session:";
    private static final String USER_PREFIX    = "user:";
    private static final Duration ROOM_TTL     = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RoomState findById(String roomId) {
        String json = redisTemplate.opsForValue().get(ROOM_PREFIX + roomId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, RoomState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize RoomState", e);
        }
    }

    public void save(RoomState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(ROOM_PREFIX + state.getRoomId(), json, ROOM_TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize RoomState", e);
        }
    }

    public void delete(String roomId) {
        redisTemplate.delete(ROOM_PREFIX + roomId);
    }

    public String findRoomIdBySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
    }

    public void saveSessionMapping(String sessionId, String roomId) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, roomId, ROOM_TTL);
    }

    public void deleteSessionMapping(String sessionId) {
        redisTemplate.delete(SESSION_PREFIX + sessionId);
    }

    public void saveUserMapping(String userId, String roomId) {
        redisTemplate.opsForValue().set(USER_PREFIX + userId, roomId, ROOM_TTL);
    }

    public String findRoomIdByUser(String userId) {
        return redisTemplate.opsForValue().get(USER_PREFIX + userId);
    }

    public void deleteUserMapping(String userId) {
        redisTemplate.delete(USER_PREFIX + userId);
    }

    public List<RoomState> findAllActive() {
        Set<String> keys = redisTemplate.keys(ROOM_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return List.of();
        return keys.stream()
                .map(key -> {
                    try {
                        return redisTemplate.opsForValue().get(key);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, RoomState.class);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
