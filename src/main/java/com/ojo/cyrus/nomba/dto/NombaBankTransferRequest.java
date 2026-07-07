package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body for POST /v2/transfers/bank (a payout to an external bank account). {@code amount} is a naira
 * decimal string (Nomba's provider boundary is naira, not kobo). {@code merchantTxRef} must be unique
 * per payout and is paired with the {@code X-Idempotent-key} header to make retries safe.
 *
 * <p>Field names follow Nomba's documented schema; verify against the sandbox during end-to-end
 * testing before relying on a live payout.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaBankTransferRequest(
        @JsonProperty("amount") String amount,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("bankCode") String bankCode,
        @JsonProperty("merchantTxRef") String merchantTxRef,
        @JsonProperty("senderName") String senderName,
        @JsonProperty("narration") String narration
) {}
