package com.rag.knowledge.dto.rag;

public record HotQuestionResponse(
        String question,
        Double score
) {
}
