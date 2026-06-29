package com.ojo.cyrus.models.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MerchantRegistrationRequest(
        @NotBlank (message = "Business name is required")
        String businessName,
        @Email(message = "Invalid email format")
        @NotBlank(message = "Business email is required")
        String businessEmail,
        @NotBlank(message = "Nomba Client Id is required")
        String nombaClientId,
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters long")
        String password,
        String nombaClientSecret,
        @NotBlank(message = "Nomba Parent Account Id is required")
        String nombaParentAccountId,
        Set<String> subAccountIds
) {}