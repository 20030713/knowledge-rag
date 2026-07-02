package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record SystemHealthResponse(
        String status,
        LocalDateTime checkedAt,
        List<SystemComponentHealthResponse> components
) {
}
