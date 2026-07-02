package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaRefreshTokenRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("refresh_token") String refreshToken
) {}
