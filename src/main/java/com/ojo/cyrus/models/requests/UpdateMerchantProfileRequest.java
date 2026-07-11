package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are changed. {@code businessEmail} and {@code password}
 * are excluded: email is the login identifier (changing it needs a verification flow) and
 * password has its own reset endpoint. {@code businessName}, unlike the other fields, may not be
 * blanked out via this endpoint — it's required on the entity and drives merchant-facing UI; a
 * present-but-blank value is rejected in {@code MerchantService.updateProfile} rather than here,
 * since bean validation can't distinguish "absent" (leave unchanged) from "blank" (reject).
 */
public record UpdateMerchantProfileRequest(
        @Schema(description = "Registered business name")
        @Size(max = 255)
        String businessName,

        @Schema(description = "Type of business (e.g. fintech, ecommerce, education)")
        @Size(max = 255)
        String businessType,

        @Schema(description = "Business phone number")
        @Size(max = 50)
        String phone,

        @Schema(description = "Bank verification number")
        @Size(max = 50)
        String bankVerificationNumber
) {}
