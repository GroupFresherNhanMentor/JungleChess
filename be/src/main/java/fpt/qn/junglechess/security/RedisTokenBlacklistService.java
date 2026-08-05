package fpt.qn.junglechess.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisTokenBlacklistService {

    ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token);
    }

    public Mono<Boolean> blacklist(String token, long ttlMillis) {
        String key = BLACKLIST_PREFIX + token;
        return redisTemplate.opsForValue()
                .set(key, "1", Duration.ofMillis(ttlMillis));
    }
}
