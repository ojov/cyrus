package com.ojo.cyrus.nomba.dto;

import java.math.BigInteger;
import java.time.Instant;

public record IncomingTransferEvent(
        String provider,               // "NOMBA"
        String eventType,              // payment_success
        String providerTransactionId,
        String requestId,

        BigInteger amount,
        String currency,

        Instant timestamp,

        PayerInfo payer,

        MerchantInfo merchant,

        String rawPayload
) {}