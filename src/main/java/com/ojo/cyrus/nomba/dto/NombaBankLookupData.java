package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code data} of a POST /v1/transfers/bank/lookup response — the verified account holder. */
public record NombaBankLookupData(
        @JsonProperty("accountName") String accountName,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("bankCode") String bankCode
) {}
