package com.ojo.cyrus.models.responses;

import java.math.BigInteger;
import java.time.Instant;

/**
 * A customer's reporting summary — always over their full history, independent of whatever
 * from/to/matchStatus filter is applied to the paginated transaction list alongside it.
 */
public record StatementSummaryResponse(
        BigInteger lifetimeKobo,
        long transactionCount,
        long pendingCount,
        BigInteger pendingKobo,
        long manualReviewCount,
        long discrepancyCount,
        Instant lastTransactionAt
) {}
