package fpt.qn.iothospital.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisTokenBlacklistService {

    static String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    StringRedisTemplate redisTemplate;

    public void blacklistToken(String tokenId, long remainingExpirationMs) {
        if (tokenId == null || tokenId.isBlank() || remainingExpirationMs <= 0) {
            return;
        }
        String key = BLACKLIST_KEY_PREFIX + tokenId;
        try {
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingExpirationMs));
            log.info("Token ID {} blacklisted in Redis for {} ms", tokenId, remainingExpirationMs);
        } catch (Exception ex) {
            log.warn("Failed to blacklist token in Redis (connection unavailable): {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        String key = BLACKLIST_KEY_PREFIX + tokenId;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception ex) {
            log.warn("Failed to check token blacklist in Redis (connection unavailable): {}", ex.getMessage());
            return false;
        }
    }
}
