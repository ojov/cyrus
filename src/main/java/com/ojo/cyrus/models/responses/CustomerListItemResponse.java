package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.enums.MerchantCustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerListItemResponse(

        UUID id,

        @Schema(description = "Your unique reference for this customer")
        String reference,

        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        MerchantCustomerStatus status,
        KycTier kycTier,

        @Schema(description = "The dedicated virtual account provisioned for this customer")
        CustomerResponse.VirtualAccountSummary virtualAccount,

        @Schema(description = "Total received (SUCCESSFUL customer payments), integer kobo")
        BigDecimal lifetimeKobo,

        Instant createdAt

) {}
