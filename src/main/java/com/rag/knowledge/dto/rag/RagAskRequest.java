package com.rag.knowledge.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RagAskRequest(
        @NotNull(message = "知识库不能为空")
        Long kbId,

        Long sessionId,

        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过500字")
        String question,

        String answerStyle,

        Integer topK,

        Double vectorWeight,

        Double keywordWeight,

        Boolean enableModel,

        Boolean enableCache
) {
}
