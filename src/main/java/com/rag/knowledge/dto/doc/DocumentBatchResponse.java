package com.rag.knowledge.dto.doc;

import java.util.List;

public record DocumentBatchResponse(
        Integer total,
        Integer submitted,
        Integer deleted,
        Integer skipped,
        List<String> messages
) {
}
