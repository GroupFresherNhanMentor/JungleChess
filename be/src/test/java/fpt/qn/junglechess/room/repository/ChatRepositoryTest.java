package fpt.qn.junglechess.room.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.qn.junglechess.room.model.ChatMessageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private ChatRepository chatRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatRepository = new ChatRepository(redisTemplate, objectMapper);
    }

    @Test
    void pushMessage_executesPipelined() {
        ChatMessageRecord msg = new ChatMessageRecord("msg-1", "u-1", "Alice", "PLAYER_1", "Hello", 1000L);
        chatRepository.pushMessage("room-123", msg);
        verify(redisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    void getRecentMessages_deserializesValidJson() throws Exception {
        ChatMessageRecord msg = new ChatMessageRecord("msg-1", "u-1", "Alice", "PLAYER_1", "Hello", 1000L);
        String json = objectMapper.writeValueAsString(msg);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("chat:room:room-123", 0, -1)).thenReturn(List.of(json));

        List<ChatMessageRecord> result = chatRepository.getRecentMessages("room-123");

        assertEquals(1, result.size());
        assertEquals("msg-1", result.get(0).id());
        assertEquals("Alice", result.get(0).senderName());
        assertEquals("Hello", result.get(0).content());
    }

    @Test
    void delete_removesKey() {
        chatRepository.delete("room-123");
        verify(redisTemplate).delete("chat:room:room-123");
    }
}
