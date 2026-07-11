package com.ojo.cyrus.utils;

import com.ojo.cyrus.config.properties.FeeProperties;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fee computation for inflows and payouts. All amounts are kobo at scale 4. */
@UtilityClass
public class FeeCalculator {

    /**
     * The merchant-facing fee for an inbound payment: {@code inflowPercent} of the gross amount,
     * clamped to {@code [inflowMinKobo, inflowMaxKobo]}. Fractional kobo is preserved at scale 4 —
     * 1.5% of 100,001 kobo is 1,500.015 kobo, not 1,500. Returns zero for a null/zero amount.
     */
    public static BigDecimal computeInflowMerchantFee(BigDecimal amountKobo, FeeProperties props) {
        if (amountKobo == null || amountKobo.signum() <= 0) {
            return MoneyUtil.ZERO_KOBO;
        }
        BigDecimal rate = props.getInflowPercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN);
        BigDecimal raw = MoneyUtil.normalize(amountKobo.multiply(rate));
        return clamp(raw, props.getInflowMinKobo(), props.getInflowMaxKobo());
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) return MoneyUtil.normalize(min);
        if (value.compareTo(max) > 0) return MoneyUtil.normalize(max);
        return value;
    }
}
