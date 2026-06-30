package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "merchant@example.com", description = "The business email address")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(example = "password123", description = "The merchant password")
        @NotBlank(message = "Password is required")
        String password
) {}
