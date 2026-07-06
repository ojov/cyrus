package com.ojo.cyrus.nomba.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Shared naira → kobo conversion for all Nomba boundary entry points (webhooks, requery,
 * balance checks). All amounts flow through here so a future rounding or null-handling change
 * applies uniformly across the money-critical path.
 */
@UtilityClass
public class NombaCurrencyUtil {

    /**
     * Converts a naira amount (as a decimal string from Nomba) to kobo (kobo as BigInteger).
     * Nomba reports: "100.0", "45000.50", etc. Convert to integer kobo using HALF_EVEN rounding.
     *
     * @param naira naira decimal string, or null/blank → returns BigInteger.ZERO
     * @return amount in kobo as BigInteger (minor units, always exact)
     */
    public static BigInteger nairaToKobo(String naira) {
        if (naira == null || naira.isBlank()) {
            return BigInteger.ZERO;
        }
        return nairaToKobo(new BigDecimal(naira));
    }

    /**
     * Converts a naira amount (as a BigDecimal, e.g. from JSON parsing) to kobo.
     *
     * @param naira naira as BigDecimal, or null → returns BigInteger.ZERO
     * @return amount in kobo as BigInteger
     */
    public static BigInteger nairaToKobo(BigDecimal naira) {
        if (naira == null) {
            return BigInteger.ZERO;
        }
        return naira
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_EVEN)
                .toBigIntegerExact();
    }
}
