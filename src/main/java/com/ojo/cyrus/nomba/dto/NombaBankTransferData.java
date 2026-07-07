package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code data} of a POST /v2/transfers/bank response. {@code status} is Nomba's transfer status
 * (e.g. SUCCESS, PENDING_BILLING, REFUND); a definitive outcome also arrives later via a
 * {@code payout_success}/{@code payout_failed} webhook. Amounts are naira decimal strings.
 */
public record NombaBankTransferData(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("merchantTxRef") String merchantTxRef,
        @JsonProperty("amount") String amount,
        @JsonProperty("fee") String fee
) {}
