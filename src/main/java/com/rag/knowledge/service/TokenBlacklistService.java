package com.rag.knowledge.service;

public interface TokenBlacklistService {

    void blacklist(String token);

    boolean blacklisted(String token);
}
