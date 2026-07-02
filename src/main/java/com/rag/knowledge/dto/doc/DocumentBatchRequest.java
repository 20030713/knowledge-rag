package com.rag.knowledge.dto.doc;

import java.util.List;

public record DocumentBatchRequest(
        List<Long> ids
) {
}
