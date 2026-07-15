package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Full virtual-account record as returned by both {@code GET /v1/accounts/virtual/{identifier}}
 * (fetch) and {@code POST /v1/accounts/virtual/list} (filter) — the two share a response item shape,
 * which is a superset of the leaner {@link NombaVirtualAccountData} returned at creation time.
 */
public record NombaVirtualAccountDetail(
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("accountHolderId") String accountHolderId,
        @JsonProperty("accountRef") String accountRef,
        @JsonProperty("bvn") String bvn,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("bankName") String bankName,
        @JsonProperty("bankAccountNumber") String bankAccountNumber,
        @JsonProperty("bankAccountName") String bankAccountName,
        @JsonProperty("currency") String currency,
        @JsonProperty("callbackUrl") String callbackUrl,
        @JsonProperty("expired") boolean expired
) {}
