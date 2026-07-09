package com.ojo.cyrus.utils;

import com.ojo.cyrus.config.properties.FeeProperties;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Fee computation for inflows and payouts. All amounts are kobo. */
@UtilityClass
public class FeeCalculator {

    /**
     * The merchant-facing fee for an inbound payment: {@code inflowPercent} of the gross amount,
     * clamped to {@code [inflowMinKobo, inflowMaxKobo]}. Returns zero for a null/zero amount.
     */
    public static BigInteger computeInflowMerchantFee(BigInteger amountKobo, FeeProperties props) {
        if (amountKobo == null || amountKobo.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigDecimal rate = props.inflowPercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN);
        BigInteger raw = new BigDecimal(amountKobo)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_EVEN)
                .toBigInteger();
        return clamp(raw, props.inflowMinKobo(), props.inflowMaxKobo());
    }

    private static BigInteger clamp(BigInteger value, BigInteger min, BigInteger max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }
}
