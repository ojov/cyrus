package com.ojo.cyrus.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Computes Cyrus's cost-plus platform fee from Nomba's confirmed fee. All amounts are kobo. */
@UtilityClass
public class FeeCalculator {

    /**
     * The total fee Cyrus charges the merchant: Nomba's fee marked up by {@code markupMultiplier}.
     * Never less than {@code providerFeeKobo} even if the multiplier is misconfigured below 1.
     */
    public static BigInteger totalPlatformFee(BigInteger providerFeeKobo, BigDecimal markupMultiplier) {
        if (providerFeeKobo == null || providerFeeKobo.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger marked = new BigDecimal(providerFeeKobo)
                .multiply(markupMultiplier)
                .setScale(0, RoundingMode.HALF_EVEN)
                .toBigInteger();
        return marked.max(providerFeeKobo);
    }
}
