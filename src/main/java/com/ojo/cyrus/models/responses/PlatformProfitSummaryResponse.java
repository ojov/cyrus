package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Platform profit summary for super-admins. Shows the expected vs actual provider balance,
 * accrued fees, and the last reconciliation snapshot.
 */
public record PlatformProfitSummaryResponse(
        @Schema(description = "Expected provider balance: merchant liabilities + accrued profit (kobo)")
        BigDecimal expectedProviderBalanceKobo,

        @Schema(description = "Actual provider balance from Nomba (fetched live, kobo; null if unavailable)")
        BigDecimal actualProviderBalanceKobo,

        @Schema(description = "expected − actual (negative = Nomba holds more than the ledger explains)")
        BigDecimal deltaKobo,

        @Schema(description = "Net confirmed inflows (gross payments received minus reversals, kobo)")
        BigDecimal totalInflowsKobo,

        @Schema(description = "Net outflows dispatched (payout amount + fee, refunds netted out; positive magnitude, kobo)")
        BigDecimal totalOutflowsKobo,

        @Schema(description = "Total platform fees accrued (merchant fee minus provider fee, kobo)")
        BigDecimal totalFeeAccrualsKobo,

        @Schema(description = "Sum of all merchant wallet balances (what Cyrus owes merchants, kobo)")
        BigDecimal merchantLiabilitiesKobo,

        @Schema(description = "Timestamp of the last successful provider balance sync")
        Instant lastSyncAt,

        @Schema(description = "Last sweep result: PENDING (no sweep yet), MATCHED, DISCREPANCY, "
                + "PROVIDER_UNAVAILABLE, or LEDGER_READ_FAILED")
        String reconciliationStatus
) {}
