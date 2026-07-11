package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Current platform fee configuration.")
public record FeeConfigResponse(
        @Schema(description = "Inbound payment fee as a percentage of the gross amount.")
        BigDecimal inflowPercent,

        @Schema(description = "Minimum inbound fee in kobo.")
        BigDecimal inflowMinKobo,

        @Schema(description = "Maximum inbound fee in kobo.")
        BigDecimal inflowMaxKobo,

        @Schema(description = "Flat fee per outbound payout transfer in kobo.")
        BigDecimal payoutFlatFeeKobo,

        @Schema(description = "When this configuration was last updated.")
        Instant updatedAt
) {}
