package com.ojo.cyrus.models.responses;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer's reporting summary — always over their full history, independent of whatever
 * from/to/matchStatus filter is applied to the paginated transaction list alongside it.
 */
public record StatementSummaryResponse(
        BigDecimal lifetimeKobo,
        long transactionCount,
        long pendingCount,
        BigDecimal pendingKobo,
        long manualReviewCount,
        long discrepancyCount,
        Instant lastTransactionAt
) {}
