package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MerchantRegistrationRequest(
        @Schema(example = "Cyrus Mobile Ltd", description = "The registered business name")
        @NotBlank (message = "Business name is required")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "The official business email")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Business email is required")
        String businessEmail,

        @Schema(example = "nomba_client_id_123", description = "Nomba client ID for integration")
        @NotBlank(message = "Nomba Client Id is required")
        String nombaClientId,

        @Schema(example = "securePassword123", description = "Login password (min 6 characters)")
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String password,

        @Schema(example = "nomba_secret_xyz", description = "Nomba client secret")
        String nombaClientSecret,

        @Schema(example = "parent_account_001", description = "Main Nomba parent account ID")
        @NotBlank(message = "Nomba Parent Account Id is required")
        String nombaParentAccountId,

        @Schema(example = "[\"sub_acc_1\", \"sub_acc_2\"]", description = "Associated sub-account IDs")
        Set<String> subAccountIds
) {}