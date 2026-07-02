package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(

        @Schema(description = "Your unique identifier for this customer in your system")
        @NotBlank
        String reference,

        @Schema(description = "Customer's first name")
        @NotBlank
        String firstName,

        @Schema(description = "Customer's last name")
        String lastName,

        @Schema(description = "Customer's email address")
        @Email
        String email,

        @Schema(description = "Customer's phone number")
        String phoneNumber,

        @Schema(description = "BVN to assign to the virtual account. If omitted, inherits the parent account BVN.")
        String bvn

) {}
