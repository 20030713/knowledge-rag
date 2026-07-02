package com.rag.knowledge.service.impl;

import com.rag.knowledge.dto.rag.HotQuestionResponse;
import com.rag.knowledge.service.HotQuestionService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class RedisHotQuestionServiceImpl implements HotQuestionService {

    private static final Logger log = LoggerFactory.getLogger(RedisHotQuestionServiceImpl.class);
    private static final String KEY_PREFIX = "rag:hot:question:";

    private final StringRedisTemplate redisTemplate;
    private final Map<Long, Map<String, Double>> localHotQuestions = new ConcurrentHashMap<>();

    public RedisHotQuestionServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void record(Long kbId, String question) {
        String normalized = normalize(question);
        if (kbId == null || normalized.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForZSet().incrementScore(key(kbId), normalized, 1.0);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, record hot question locally", exception);
            localHotQuestions.computeIfAbsent(kbId, ignored -> new ConcurrentHashMap<>())
                    .merge(normalized, 1.0, Double::sum);
        }
    }

    @Override
    public List<HotQuestionResponse> list(Long kbId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 10 : limit, 50));
        try {
            var tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key(kbId), 0, safeLimit - 1);
            if (tuples == null) {
                return List.of();
            }
            return tuples.stream()
                    .map(this::toResponse)
                    .toList();
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, list local hot questions only", exception);
            return localHotQuestions.getOrDefault(kbId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                    .limit(safeLimit)
                    .map(entry -> new HotQuestionResponse(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    private HotQuestionResponse toResponse(ZSetOperations.TypedTuple<String> tuple) {
        return new HotQuestionResponse(tuple.getValue(), tuple.getScore() == null ? 0.0 : tuple.getScore());
    }

    private String key(Long kbId) {
        return KEY_PREFIX + kbId;
    }

    private String normalize(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
