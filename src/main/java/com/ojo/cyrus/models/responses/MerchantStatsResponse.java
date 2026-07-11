package com.ojo.cyrus.models.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Operational snapshot for the merchant dashboard. Wallet balance is integer kobo. */
public record MerchantStatsResponse(
        long customers,
        long virtualAccounts,
        BigDecimal walletBalance,
        ReconciliationSummary reconciliation,
        List<DailyInflow> inflowLast7Days
) {
    /**
     * Reconciliation health across both transactions (already-attributed payments) and payment
     * events (some of which never became a transaction at all — an orphan/misdirected payment).
     */
    public record ReconciliationSummary(
            long matched,
            long discrepancy,
            long manualReview,
            long pending,
            long orphaned
    ) {}

    /** One day's total confirmed inbound volume (kobo), for the last-7-days sparkline. */
    public record DailyInflow(
            LocalDate date,
            BigDecimal amountKobo
    ) {}
}
