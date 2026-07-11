package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** A single transaction on your books — an inbound customer payment or an outbound payout. */
public record TransactionResponse(
        String reference,
        TransactionType type,
        String customerReference,
        Instant date,
        String payer,
        String providerTransactionId,
        TransactionStatus status,
        MatchStatus matchStatus,
        BigDecimal amountKobo,
        @Schema(description = """
                Fee on this transaction in kobo — the total fee charged to the merchant.
                For CUSTOMER_PAYMENT: inflowPercent of the gross amount, clamped to [inflowMinKobo, inflowMaxKobo]
                (covers both Nomba's processing fee and Cyrus's platform margin).
                For PAYOUT: Cyrus's flat payoutFlatFeeKobo.
                For REVERSAL/ADJUSTMENT: zero — fees are never assessed on adjustments.
                The amount credited to your wallet = amountKobo − feeKobo (for CUSTOMER_PAYMENT)
                or amountKobo + feeKobo is the total debit from your wallet (for PAYOUT).""")
        BigDecimal feeKobo
) {}
