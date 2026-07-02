package com.rag.knowledge.dto.rag;

public record ChatModelStatusResponse(
        boolean enabled,
        boolean apiKeyConfigured,
        boolean available,
        String baseUrl,
        String endpointPath,
        String model,
        boolean thinkingEnabled,
        String reasoningEffort
) {
}
