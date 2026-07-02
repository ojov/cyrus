package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record NombaTokenData(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expiresAt") Instant expiresAt
) {}
