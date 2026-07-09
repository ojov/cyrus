package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/**
 * Platform-wide oversight snapshot for super-admins. All money is integer kobo. Assembled by
 * {@code PlatformAdminService.getOverview}.
 */
public record PlatformOverviewResponse(
        Custody custody,
        Totals totals,
        ReconciliationHealth reconciliation,
        OrphansAndStuck orphansAndStuck,
        LedgerIntegrity ledgerIntegrity
) {

    /**
     * What Cyrus owes merchants vs what it actually holds at the provider.
     *
     * @param walletLiabilitiesKobo sum of every merchant wallet balance (what Cyrus owes)
     * @param nombaBalanceKobo      live Nomba sub-account balance — {@code null} if the provider call
     *                              failed (the rest of the snapshot is still returned)
     * @param coverageKobo          nombaBalance − liabilities (positive = fully covered); null if the
     *                              balance couldn't be fetched
     */
    public record Custody(
            BigInteger walletLiabilitiesKobo,
            @Schema(nullable = true) BigInteger nombaBalanceKobo,
            @Schema(nullable = true) BigInteger coverageKobo,
            boolean nombaBalanceAvailable
    ) {}

    public record Totals(
            long merchants,
            long customers,
            long virtualAccounts,
            long transactions,
            BigInteger totalConfirmedInflowKobo,
            BigInteger totalPayoutsKobo
    ) {}

    public record ReconciliationHealth(
            long matched,
            long discrepancy,
            long manualReview,
            long pending
    ) {}

    public record OrphansAndStuck(
            @Schema(description = "Payment events with no owning merchant — invisible everywhere else")
            long unattributedOrphans,
            @Schema(description = "Payouts accepted but never completed by a webhook (wallet already debited)")
            long stuckPayouts,
            List<StuckPayout> stuckPayoutDetails
    ) {
        public record StuckPayout(UUID id, String reference, String merchantName, BigInteger amountKobo, String createdAt) {}
    }

    /**
     * Double-entry invariant check: each wallet's summed signed ledger entries must equal its stored
     * balance. {@code mismatches} lists any that don't reconcile (should always be empty).
     */
    public record LedgerIntegrity(
            long walletsChecked,
            long mismatchCount,
            boolean allReconciled,
            List<WalletMismatch> mismatches
    ) {
        public record WalletMismatch(UUID walletId, String merchantName, BigInteger balanceKobo, BigInteger ledgerSumKobo) {}
    }
}
