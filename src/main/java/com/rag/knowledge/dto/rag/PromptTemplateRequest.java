package com.rag.knowledge.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptTemplateRequest(
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 80, message = "模板名称不能超过80字")
        String name,

        String answerStyle,

        @NotBlank(message = "模板内容不能为空")
        @Size(max = 5000, message = "模板内容不能超过5000字")
        String systemPrompt,

        Boolean enabled
) {
}
