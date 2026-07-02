package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record SystemDiagnosticsResponse(
        String status,
        LocalDateTime checkedAt,
        List<DiagnosticItemResponse> items
) {
}
