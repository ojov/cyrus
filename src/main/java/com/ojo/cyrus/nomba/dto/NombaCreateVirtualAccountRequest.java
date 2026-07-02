package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaCreateVirtualAccountRequest(
        @JsonProperty("accountRef") String accountRef,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("bvn") String bvn
) {}
