package com.ojo.cyrus.utils;

import com.ojo.cyrus.config.properties.FeeProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeProperties props = new FeeProperties(
            new BigDecimal("1.5"),
            new BigDecimal("1500"),
            new BigDecimal("22500"),
            new BigDecimal("3000")
    );

    @Test
    void standardFee() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("1000000"), props);
        // 1.5% of ₦10,000 = ₦150 → 15000 kobo (within min/max)
        assertThat(fee).isEqualByComparingTo("15000");
    }

    @Test
    void belowMinimumClamps() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("50000"), props);
        // 1.5% of ₦500 = ₦7.50 → clamped to ₦15 minimum (1500 kobo)
        assertThat(fee).isEqualByComparingTo("1500");
    }

    @Test
    void aboveMaximumClamps() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("50000000"), props);
        // 1.5% of ₦500,000 = ₦7,500 → clamped to ₦225 maximum (22500 kobo)
        assertThat(fee).isEqualByComparingTo("22500");
    }

    @Test
    void zeroAmount() {
        assertThat(FeeCalculator.computeInflowMerchantFee(BigDecimal.ZERO, props))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullAmount() {
        assertThat(FeeCalculator.computeInflowMerchantFee(null, props))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void exactlyMinimum() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("100000"), props);
        // 1.5% of ₦1,000 = ₦15 → exactly minimum (1500 kobo)
        assertThat(fee).isEqualByComparingTo("1500");
    }

    @Test
    void exactlyMaximum() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("15000000"), props);
        // 1.5% of ₦150,000 = ₦2,250 → exactly maximum (22500 kobo)
        assertThat(fee).isEqualByComparingTo("22500");
    }

    @Test
    void withinBoundsNoClamping() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("500000"), props);
        // 1.5% of ₦5,000 = ₦75 = 7500 kobo. Within bounds, no clamping.
        assertThat(fee).isEqualByComparingTo("7500");
    }

    @Test
    void largeAmountClampsToMax() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("12500000"), props);
        // 1.5% of ₦125,000 = ₦1,875 → clamped to ₦225 max (22500 kobo)
        assertThat(fee).isEqualByComparingTo("22500");
    }

    // ---- Sub-kobo precision: fractional fees survive instead of rounding to whole kobo ----

    @Test
    void fractionalFeePreserved() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("100001"), props);
        // 1.5% of 100,001 kobo = 1500.015 kobo — the fractional remainder is kept, not rounded away.
        assertThat(fee).isEqualByComparingTo("1500.015");
    }

    @Test
    void fractionalInputAmountPreserved() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("500033.5"), props);
        // 1.5% of 500,033.5 kobo = 7500.5025 kobo, within bounds.
        assertThat(fee).isEqualByComparingTo("7500.5025");
    }

    @Test
    void resultIsCanonicalScale4() {
        BigDecimal fee = FeeCalculator.computeInflowMerchantFee(new BigDecimal("1000000"), props);
        assertThat(fee.scale()).isEqualTo(MoneyUtil.KOBO_SCALE);
    }
}
