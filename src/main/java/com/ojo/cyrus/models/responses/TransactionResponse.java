package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;

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
        BigInteger feeKobo
) {}
