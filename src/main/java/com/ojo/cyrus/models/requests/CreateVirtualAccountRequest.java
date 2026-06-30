package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateVirtualAccountRequest(
        @Schema(example = "unique_ref_12345", description = "Unique reference for the customer in your system")
        @NotBlank
        String customerReference,

        @Schema(example = "John Doe", description = "Full name of the customer")
        @NotBlank
        String customerName,

        @Schema(example = "john.doe@example.com", description = "Email address of the customer")
        @Email
        String customerEmail

) {}