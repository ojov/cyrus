package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Upper bounds are sanity guards, not business limits — this takes effect immediately for every
 * merchant on the platform, so a fat-fingered extra digit (e.g. 150 instead of 1.5, or 2250000
 * instead of 22500) must not be able to silently overcharge every subsequent transaction.
 * Kobo values are admin-entered and stay whole-kobo-valued ({@code @Digits(fraction = 0)});
 * fractional kobo only ever originates in computed fees, not configuration.
 */
@Schema(description = "Fee configuration update — all fields are required.")
public record UpdateFeeConfigRequest(
        @NotNull(message = "inflowPercent is required")
        @DecimalMin(value = "0.01", message = "inflowPercent must be at least 0.01%")
        @DecimalMax(value = "20", message = "inflowPercent must not exceed 20%")
        @Schema(description = "Inbound payment fee as a percentage of the gross amount (e.g. 1.5 = 1.5%).", example = "1.5")
        BigDecimal inflowPercent,

        @NotNull(message = "inflowMinKobo is required")
        @PositiveOrZero(message = "inflowMinKobo must not be negative")
        @DecimalMax(value = "10000000", message = "inflowMinKobo must not exceed 10,000,000 kobo (₦100,000)")
        @Digits(integer = 34, fraction = 0, message = "inflowMinKobo must be a whole number of kobo")
        @Schema(description = "Minimum inbound fee in kobo (clamped floor). Zero means no floor.", example = "1500")
        BigDecimal inflowMinKobo,

        @NotNull(message = "inflowMaxKobo is required")
        @Positive(message = "inflowMaxKobo must be positive")
        @DecimalMax(value = "10000000", message = "inflowMaxKobo must not exceed 10,000,000 kobo (₦100,000)")
        @Digits(integer = 34, fraction = 0, message = "inflowMaxKobo must be a whole number of kobo")
        @Schema(description = "Maximum inbound fee in kobo (clamped ceiling).", example = "22500")
        BigDecimal inflowMaxKobo,

        @NotNull(message = "payoutFlatFeeKobo is required")
        @PositiveOrZero(message = "payoutFlatFeeKobo must not be negative")
        @DecimalMax(value = "10000000", message = "payoutFlatFeeKobo must not exceed 10,000,000 kobo (₦100,000)")
        @Digits(integer = 34, fraction = 0, message = "payoutFlatFeeKobo must be a whole number of kobo")
        @Schema(description = "Flat fee per outbound payout transfer in kobo. Zero means free payouts.", example = "3000")
        BigDecimal payoutFlatFeeKobo
) {}
