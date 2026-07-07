package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Merchant sign-up. Merchants no longer supply Nomba credentials — Cyrus provisions everything under
 * its own single Nomba account — so registration only needs the business identity + a password.
 */
public record MerchantRegistrationRequest(
        @Schema(example = "Cyrus Mobile Ltd", description = "The registered business name")
        @NotBlank(message = "Business name is required")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "The official business email")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Business email is required")
        String businessEmail,

        @Schema(example = "securePassword123", description = "Login password (min 6 characters)")
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String password
) {}
