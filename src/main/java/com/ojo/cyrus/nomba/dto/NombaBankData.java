package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One entry from GET /v1/transfers/bank — a payable bank and its NIP code. */
public record NombaBankData(
        @JsonProperty("name") String name,
        @JsonProperty("code") String code
) {}
