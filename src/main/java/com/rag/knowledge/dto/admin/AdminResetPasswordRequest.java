package com.rag.knowledge.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank(message = "new password is required")
        @Size(min = 6, max = 64, message = "password length must be 6-64")
        String newPassword
) {
}
