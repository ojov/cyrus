package com.ojo.cyrus.models.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateVirtualAccountRequest(
        @NotBlank
        String customerReference,

        @NotBlank
        String customerName,

        @Email
        String customerEmail

) {}