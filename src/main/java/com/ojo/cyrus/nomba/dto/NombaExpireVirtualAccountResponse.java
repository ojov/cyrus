package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaExpireVirtualAccountResponse(
        @JsonProperty("expired") boolean expired
) {}
