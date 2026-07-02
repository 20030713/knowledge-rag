package com.rag.knowledge.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeConfigResponse(
        LocalDateTime checkedAt,
        List<RuntimeConfigItemResponse> items
) {
}
