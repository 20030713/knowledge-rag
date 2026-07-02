package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;

public record RecentQuestionResponse(
        Long id,
        Long kbId,
        String question,
        Integer hitCount,
        LocalDateTime createdAt
) {
}
