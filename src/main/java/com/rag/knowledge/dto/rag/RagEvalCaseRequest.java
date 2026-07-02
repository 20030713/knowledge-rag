package com.rag.knowledge.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagEvalCaseRequest(
        @NotBlank(message = "评测问题不能为空")
        @Size(max = 500, message = "评测问题不能超过500字")
        String question,

        @NotBlank(message = "期望答案不能为空")
        @Size(max = 2000, message = "期望答案不能超过2000字")
        String expectedAnswer,

        @Size(max = 500, message = "关键词不能超过500字")
        String expectedKeywords,

        Boolean enabled
) {
}
