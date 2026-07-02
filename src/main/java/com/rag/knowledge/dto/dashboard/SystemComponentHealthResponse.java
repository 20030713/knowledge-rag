package com.rag.knowledge.dto.dashboard;

public record SystemComponentHealthResponse(
        String name,
        String status,
        String message,
        Long latencyMs
) {
}
