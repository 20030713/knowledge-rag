package com.rag.knowledge.service;

import java.time.Duration;

public interface DistributedLockService {

    LockAttempt tryLock(String key, Duration ttl);

    void unlock(DistributedLock lock);

    record LockAttempt(boolean locked, boolean available, DistributedLock lock) {
    }

    record DistributedLock(String key, String value) {
    }
}
