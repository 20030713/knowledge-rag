package com.rag.knowledge.service.impl;

import com.rag.knowledge.service.DistributedLockService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisDistributedLockServiceImpl implements DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockServiceImpl.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public LockAttempt tryLock(String key, Duration ttl) {
        String value = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
            if (Boolean.TRUE.equals(locked)) {
                return new LockAttempt(true, true, new DistributedLock(key, value));
            }
            return new LockAttempt(false, true, null);
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip distributed lock key={}", key, exception);
            return new LockAttempt(false, false, null);
        }
    }

    @Override
    public void unlock(DistributedLock lock) {
        if (lock == null) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lock.key()), lock.value());
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis unavailable, skip distributed unlock key={}", lock.key(), exception);
        }
    }
}
