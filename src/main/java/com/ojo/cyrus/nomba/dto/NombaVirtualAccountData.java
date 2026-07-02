package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaVirtualAccountData(
        @JsonProperty("bankAccountNumber") String bankAccountNumber,
        @JsonProperty("bankAccountName") String bankAccountName,
        @JsonProperty("bankName") String bankName,
        @JsonProperty("accountRef") String accountRef,
        @JsonProperty("accountHolderId") String accountHolderId,
        @JsonProperty("currency") String currency
) {}
