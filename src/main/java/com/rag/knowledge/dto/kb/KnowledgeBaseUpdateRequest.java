package com.rag.knowledge.dto.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        @Size(max = 512, message = "知识库描述不能超过 512 个字符")
        String description,

        @Min(value = 200, message = "切片长度不能小于 200")
        @Max(value = 1500, message = "切片长度不能大于 1500")
        Integer chunkSize,

        @Min(value = 0, message = "重叠长度不能小于 0")
        @Max(value = 300, message = "重叠长度不能大于 300")
        Integer chunkOverlap,

        @Min(value = 50, message = "最小切分位置不能小于 50")
        @Max(value = 1200, message = "最小切分位置不能大于 1200")
        Integer minBreakSize
) {
}
