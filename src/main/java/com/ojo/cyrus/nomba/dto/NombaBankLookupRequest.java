package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body for POST /v1/transfers/bank/lookup — resolves the account holder name before a payout. */
public record NombaBankLookupRequest(
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("bankCode") String bankCode
) {}
