package com.ojo.cyrus.models.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RegisterMerchantRequest (
        @NotBlank (message = "Business name is required")
        String businessName,
        @Email(message = "Invalid email format")
        @NotBlank(message = "Business email is required")
        String businessEmail,
        @NotBlank(message = "Nomba Client Id is required")
        String nombaClientId,
        String nombaClientSecret,
        @NotBlank(message = "Nomba Parent Account Id is required")
        String nombaParentAccountId,
        List<String> subAccountIds

) {}