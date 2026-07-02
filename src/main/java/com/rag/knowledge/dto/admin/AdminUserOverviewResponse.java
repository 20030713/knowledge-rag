package com.rag.knowledge.dto.admin;

public record AdminUserOverviewResponse(
        Long totalCount,
        Long enabledCount,
        Long disabledCount,
        Long adminCount
) {
}
