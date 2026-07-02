package com.rag.knowledge.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "old password is required")
        String oldPassword,

        @NotBlank(message = "new password is required")
        @Size(min = 6, max = 64, message = "password length must be 6-64")
        String newPassword
) {
}
