package com.ojo.cyrus.nomba.utils;

import com.ojo.cyrus.utils.MoneyUtil;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * Shared naira → kobo conversion for all Nomba boundary entry points (webhooks, requery,
 * balance checks). All amounts flow through here so a future rounding or null-handling change
 * applies uniformly across the money-critical path.
 *
 * <p>Fractional kobo is preserved: Nomba reports naira decimal strings, and any sub-kobo remainder
 * from the ×100 scaling survives at {@link MoneyUtil#KOBO_SCALE scale 4} rather than being rounded
 * to a whole kobo (the pre-BigDecimal behaviour). Rounding to whole kobo happens only at true
 * settlement edges (see {@code PayoutService.koboToNaira}).
 */
@UtilityClass
public class NombaCurrencyUtil {

    /**
     * Converts a naira amount (as a decimal string from Nomba, e.g. "100.0", "45000.505") to kobo
     * at canonical scale 4.
     *
     * @param naira naira decimal string, or null/blank → returns {@link MoneyUtil#ZERO_KOBO}
     * @return amount in kobo as scale-4 BigDecimal
     */
    public static BigDecimal nairaToKobo(String naira) {
        if (naira == null || naira.isBlank()) {
            return MoneyUtil.ZERO_KOBO;
        }
        return nairaToKobo(new BigDecimal(naira));
    }

    /**
     * Converts a naira amount (as a BigDecimal, e.g. from JSON parsing) to kobo at canonical scale 4.
     *
     * @param naira naira as BigDecimal, or null → returns {@link MoneyUtil#ZERO_KOBO}
     * @return amount in kobo as scale-4 BigDecimal
     */
    public static BigDecimal nairaToKobo(BigDecimal naira) {
        if (naira == null) {
            return MoneyUtil.ZERO_KOBO;
        }
        return MoneyUtil.normalize(naira.movePointRight(2));
    }
}
