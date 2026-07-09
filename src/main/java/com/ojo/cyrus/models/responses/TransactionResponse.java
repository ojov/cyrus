package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
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
        BigInteger amountKobo,
        @Schema(description = """
                Fee on this transaction in kobo.
                For CUSTOMER_PAYMENT: Nomba's own fee (1% min ₦10, max ₦150).
                For PAYOUT: Cyrus's flat ₦30 fee.
                For REVERSAL/ADJUSTMENT: zero — fees are never assessed on adjustments.
                The amount credited to your wallet = amountKobo − feeKobo (for CUSTOMER_PAYMENT)
                or amountKobo + feeKobo is the total debit from your wallet (for PAYOUT).""")
        BigInteger feeKobo
) {}
