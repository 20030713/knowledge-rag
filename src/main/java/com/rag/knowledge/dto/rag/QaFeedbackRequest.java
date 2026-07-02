package com.rag.knowledge.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record QaFeedbackRequest(
        @Min(-1)
        @Max(1)
        Integer feedbackScore,

        @Size(max = 500)
        String feedbackNote
) {
}
