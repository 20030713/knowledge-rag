package com.rag.knowledge.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.RagCacheProperties;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.service.RagAnswerCacheService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRagAnswerCacheServiceImpl implements RagAnswerCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisRagAnswerCacheServiceImpl.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagCacheProperties properties;

    public RedisRagAnswerCacheServiceImpl(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RagCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<RagAskResponse> get(Long userId, Long kbId, String question) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String key = buildKey(userId, kbId, question);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, RagAskResponse.class));
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip RAG answer cache get key={}", key, exception);
            return Optional.empty();
        } catch (JsonProcessingException exception) {
            log.warn("Invalid RAG answer cache payload key={}", key, exception);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(Long userId, Long kbId, String question, RagAskResponse response) {
        if (!properties.isEnabled() || properties.getTtlMinutes() <= 0) {
            return;
        }
        String key = buildKey(userId, kbId, question);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(properties.getTtlMinutes()));
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip RAG answer cache put key={}", key, exception);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize RAG answer cache key={}", key, exception);
        }
    }

    @Override
    public void evictKnowledgeBase(Long userId, Long kbId) {
        evictByPattern("rag:answer:user:%s:kb:%s:q:*".formatted(userId, kbId));
    }

    @Override
    public void evictKnowledgeBase(Long kbId) {
        evictByPattern("rag:answer:user:*:kb:%s:q:*".formatted(kbId));
    }

    private void evictByPattern(String pattern) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build())) {
                    while (cursor.hasNext()) {
                        connection.del(cursor.next());
                    }
                }
                return null;
            });
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip RAG answer cache eviction pattern={}", pattern, exception);
        }
    }

    private String buildKey(Long userId, Long kbId, String question) {
        return "rag:answer:user:%s:kb:%s:q:%s".formatted(userId, kbId, sha256(normalizeQuestion(question)));
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
