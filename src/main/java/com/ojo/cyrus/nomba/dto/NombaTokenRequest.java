package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NombaTokenRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_secret") String clientSecret
) {}
