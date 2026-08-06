package fpt.qn.junglechess.room.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.qn.junglechess.room.model.RoomState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RoomStateRepository {

    private static final String ROOM_PREFIX    = "room:";
    private static final String SESSION_PREFIX = "session:";
    private static final Duration ROOM_TTL     = Duration.ofHours(2);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<RoomState> findById(String roomId) {
        return redisTemplate.opsForValue()
                .get(ROOM_PREFIX + roomId)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, RoomState.class));
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to deserialize RoomState", e));
                    }
                });
    }

    public Mono<Void> save(RoomState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            return redisTemplate.opsForValue()
                    .set(ROOM_PREFIX + state.getRoomId(), json, ROOM_TTL)
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize RoomState", e));
        }
    }

    public Mono<Void> delete(String roomId) {
        return redisTemplate.delete(ROOM_PREFIX + roomId).then();
    }

    public Mono<String> findRoomIdBySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
    }

    public Mono<Void> saveSessionMapping(String sessionId, String roomId) {
        return redisTemplate.opsForValue()
                .set(SESSION_PREFIX + sessionId, roomId, ROOM_TTL)
                .then();
    }

    public Mono<Void> deleteSessionMapping(String sessionId) {
        return redisTemplate.delete(SESSION_PREFIX + sessionId).then();
    }

    public Flux<RoomState> findAllActive() {
        return redisTemplate.keys(ROOM_PREFIX + "*")
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, RoomState.class));
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
    }
}
