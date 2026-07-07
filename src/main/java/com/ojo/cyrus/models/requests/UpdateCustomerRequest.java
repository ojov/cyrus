package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

/**
 * Partial update — only non-null fields are changed. {@code reference} is the merchant's stable
 * identity key and is never renameable here (nor is any KYC/status field — those have dedicated
 * endpoints). This updates the customer's own profile only; it does NOT change the virtual
 * account's bank account name, which is fixed by Nomba at issuance and cannot be renamed via
 * their API.
 */
public record UpdateCustomerRequest(
        @Schema(description = "Customer's first name")
        String firstName,

        @Schema(description = "Customer's last name")
        String lastName,

        @Schema(description = "Customer's email address")
        @Email
        String email,

        @Schema(description = "Customer's phone number")
        String phoneNumber
) {}
