package com.rag.knowledge.dto.user;

public record UserPreferenceResponse(
        String defaultAnswerStyle,
        Integer defaultTopK,
        Double vectorWeight,
        Double keywordWeight,
        Boolean enableModel,
        Boolean enableCache
) {
}
