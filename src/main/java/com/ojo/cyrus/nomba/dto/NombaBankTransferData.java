package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code data} of a POST /v2/transfers/bank or GET /v1/transactions/accounts/{subAccountId}/single
 * response. {@code status} is Nomba's transfer status: {@code SUCCESS} (settled),
 * {@code PENDING_BILLING} (accepted but not yet settled — await the webhook), {@code REFUND}
 * (reversed), or {@code FAILED}. A definitive outcome also arrives via a
 * {@code payout_success}/{@code payout_failed}/{@code payout_refund} webhook. Amounts are naira
 * decimal strings. Live-verified: a {@code GET} requery of a settled transfer returns
 * {@code status: "SUCCESS"} with the full transfer detail including {@code fixedCharge}.
 */
public record NombaBankTransferData(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("merchantTxRef") String merchantTxRef,
        @JsonProperty("amount") String amount,
        @JsonProperty("fee") String fee
) {}
