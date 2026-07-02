package com.rag.knowledge.dto.dashboard;

import java.util.List;

public record DiagnosticItemResponse(
        String key,
        String label,
        String status,
        String message,
        Long latencyMs,
        List<String> details
) {
}
