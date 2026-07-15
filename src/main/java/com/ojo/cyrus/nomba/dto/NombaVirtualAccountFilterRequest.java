package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional body filters for {@code POST /v1/accounts/virtual/list}
 * ({@link com.ojo.cyrus.nomba.NombaApiUri#VIRTUAL_ACCOUNT_LIST}) — {@code limit}/{@code cursor} are
 * query params, these narrow further. All null (an empty body) lists everything in the page window.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaVirtualAccountFilterRequest(
        @JsonProperty("accountRef") String accountRef,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("bvn") String bvn,
        @JsonProperty("bankAccountNumber") String bankAccountNumber,
        @JsonProperty("dateCreatedFrom") String dateCreatedFrom,
        @JsonProperty("dateCreatedTo") String dateCreatedTo,
        @JsonProperty("expired") Boolean expired,
        @JsonProperty("resourceAcquired") Boolean resourceAcquired
) {
    public static NombaVirtualAccountFilterRequest empty() {
        return new NombaVirtualAccountFilterRequest(null, null, null, null, null, null, null, null);
    }
}
