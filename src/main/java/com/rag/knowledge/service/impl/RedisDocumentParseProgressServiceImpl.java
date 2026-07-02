package com.rag.knowledge.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.dto.doc.DocumentParseProgressResponse;
import com.rag.knowledge.service.DocumentParseProgressService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisDocumentParseProgressServiceImpl implements DocumentParseProgressService {

    private static final Logger log = LoggerFactory.getLogger(RedisDocumentParseProgressServiceImpl.class);
    private static final String KEY_PREFIX = "rag:doc:progress:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Long, DocumentParseProgressResponse> localProgress = new ConcurrentHashMap<>();

    public RedisDocumentParseProgressServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void mark(Long documentId, String stage, int percent, String message, int processedChunks, int totalChunks) {
        if (documentId == null) {
            return;
        }
        DocumentParseProgressResponse progress = new DocumentParseProgressResponse(
                documentId,
                stage,
                clampPercent(percent),
                message,
                Math.max(0, processedChunks),
                Math.max(0, totalChunks),
                LocalDateTime.now()
        );
        localProgress.put(documentId, progress);
        try {
            redisTemplate.opsForValue().set(key(documentId), objectMapper.writeValueAsString(progress), TTL);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, keep document parse progress locally documentId={}", documentId, exception);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize document parse progress documentId={}", documentId, exception);
        }
    }

    @Override
    public Optional<DocumentParseProgressResponse> get(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(documentId));
            if (json != null && !json.isBlank()) {
                return Optional.of(objectMapper.readValue(json, DocumentParseProgressResponse.class));
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, read local document parse progress documentId={}", documentId, exception);
        } catch (JsonProcessingException exception) {
            log.warn("Invalid document parse progress payload documentId={}", documentId, exception);
            redisTemplate.delete(key(documentId));
        }
        return Optional.ofNullable(localProgress.get(documentId));
    }

    @Override
    public void clear(Long documentId) {
        if (documentId == null) {
            return;
        }
        localProgress.remove(documentId);
        try {
            redisTemplate.delete(key(documentId));
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip document parse progress delete documentId={}", documentId, exception);
        }
    }

    private String key(Long documentId) {
        return KEY_PREFIX + documentId;
    }

    private int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
