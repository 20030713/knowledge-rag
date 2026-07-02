package com.rag.knowledge.dto.dashboard;

import java.util.List;

public record RuntimeConfigItemResponse(
        String key,
        String label,
        String status,
        String summary,
        List<String> details
) {
}
