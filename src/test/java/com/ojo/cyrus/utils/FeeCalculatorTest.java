package com.ojo.cyrus.utils;

import com.ojo.cyrus.config.properties.FeeProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeCalculatorTest {

    private final FeeProperties props = new FeeProperties(
            new BigDecimal("1.5"),
            new BigInteger("1500"),
            new BigInteger("22500"),
            new BigInteger("3000")
    );

    @Test
    void standardFee() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("1000000"), props);
        // 1.5% of ₦10,000 = ₦150 → 15000 kobo (within min/max)
        assertEquals(new BigInteger("15000"), fee);
    }

    @Test
    void belowMinimumClamps() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("50000"), props);
        // 1.5% of ₦500 = ₦7.50 → clamped to ₦15 minimum (1500 kobo)
        assertEquals(new BigInteger("1500"), fee);
    }

    @Test
    void aboveMaximumClamps() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("50000000"), props);
        // 1.5% of ₦500,000 = ₦7,500 → clamped to ₦225 maximum (22500 kobo)
        assertEquals(new BigInteger("22500"), fee);
    }

    @Test
    void zeroAmount() {
        assertEquals(BigInteger.ZERO, FeeCalculator.computeInflowMerchantFee(BigInteger.ZERO, props));
    }

    @Test
    void nullAmount() {
        assertEquals(BigInteger.ZERO, FeeCalculator.computeInflowMerchantFee(null, props));
    }

    @Test
    void exactlyMinimum() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("100000"), props);
        // 1.5% of ₦1,000 = ₦15 → exactly minimum (1500 kobo)
        assertEquals(new BigInteger("1500"), fee);
    }

    @Test
    void exactlyMaximum() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("15000000"), props);
        // 1.5% of ₦150,000 = ₦2,250 → exactly maximum (22500 kobo)
        assertEquals(new BigInteger("22500"), fee);
    }

    @Test
    void roundingHalfEven() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("500000"), props);
        // 1.5% of ₦5,000 = ₦75 = 7500 kobo. Within bounds, no clamping.
        assertEquals(new BigInteger("7500"), fee);
    }

    @Test
    void largeAmountWithinCaps() {
        BigInteger fee = FeeCalculator.computeInflowMerchantFee(new BigInteger("12500000"), props);
        // 1.5% of ₦125,000 = ₦1,875 → clamped to ₦225 max (22500 kobo)
        assertEquals(new BigInteger("22500"), fee);
    }
}
