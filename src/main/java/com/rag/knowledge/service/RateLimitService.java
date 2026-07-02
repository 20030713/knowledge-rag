package com.rag.knowledge.service;

import com.rag.knowledge.config.RateLimitProperties;

public interface RateLimitService {

    void check(String key, RateLimitProperties.Rule rule);
}
