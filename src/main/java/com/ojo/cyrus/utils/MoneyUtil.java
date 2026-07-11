package com.ojo.cyrus.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Canonical money representation: {@link BigDecimal} kobo at scale {@value #KOBO_SCALE},
 * {@code HALF_EVEN}. Fractional kobo is preserved through ingestion, fee math, ledger entries and
 * wallet balances; rounding to whole kobo happens only at the Nomba payout edge
 * ({@code PayoutService.koboToNaira}).
 *
 * <p>Normalize wherever a money value is <em>produced</em> (provider conversion, fee computation,
 * ledger postings, admin-supplied config) so persisted values are always scale-4. Never compare
 * money with {@code equals()} — {@code BigDecimal.equals} is scale-sensitive ({@code 12000} ≠
 * {@code 12000.0000}); use {@code compareTo}/{@code signum}.
 */
@UtilityClass
public class MoneyUtil {

    public static final int KOBO_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO_KOBO = BigDecimal.ZERO.setScale(KOBO_SCALE, ROUNDING);

    /** Canonical scale-{@value #KOBO_SCALE} kobo; null-safe (null → null). */
    public static BigDecimal normalize(BigDecimal kobo) {
        return kobo == null ? null : kobo.setScale(KOBO_SCALE, ROUNDING);
    }
}
