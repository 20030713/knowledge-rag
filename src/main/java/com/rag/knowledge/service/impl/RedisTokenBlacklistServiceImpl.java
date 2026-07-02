package com.rag.knowledge.service.impl;

import com.rag.knowledge.config.AuthProperties;
import com.rag.knowledge.service.TokenBlacklistService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisTokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistServiceImpl.class);
    private static final String KEY_PREFIX = "rag:blacklist:token:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final Map<String, Long> localBlacklist = new ConcurrentHashMap<>();

    public RedisTokenBlacklistServiceImpl(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    @Override
    public void blacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String tokenHash = hash(token);
        Duration ttl = Duration.ofHours(Math.max(1, authProperties.getExpireHours()));
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + tokenHash, "1", ttl);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, store token blacklist locally", exception);
            localBlacklist.put(tokenHash, System.currentTimeMillis() + ttl.toMillis());
        }
    }

    @Override
    public boolean blacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String tokenHash = hash(token);
        Long expiresAt = localBlacklist.get(tokenHash);
        if (expiresAt != null) {
            if (expiresAt > System.currentTimeMillis()) {
                return true;
            }
            localBlacklist.remove(tokenHash);
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenHash));
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, check local token blacklist only", exception);
            return false;
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
