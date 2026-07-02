package com.rag.knowledge.service.impl;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.RateLimitProperties;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.service.RateLimitService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimitServiceImpl implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitServiceImpl.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void check(String key, RateLimitProperties.Rule rule) {
        if (rule.limit() <= 0 || rule.windowSeconds() <= 0) {
            return;
        }

        String redisKey = "rate_limit:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(rule.windowSeconds()));
            }
            if (count != null && count > rule.limit()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip rate limit key={}", redisKey, exception);
        }
    }
}
