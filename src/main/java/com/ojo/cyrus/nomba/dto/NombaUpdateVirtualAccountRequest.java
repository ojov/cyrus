package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PUT /v1/accounts/virtual/{identifier} body. {@code @JsonInclude(NON_NULL)} is deliberate: Nomba's
 * schema also accepts {@code newAccountRef}, {@code callbackUrl}, {@code expectedAmount}, and we
 * never want to risk sending an explicit {@code null} for those and having Nomba interpret it as
 * "clear this field" — we only ever want to touch {@code accountName}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaUpdateVirtualAccountRequest(
        @JsonProperty("accountName") String accountName
) {}
