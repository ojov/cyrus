package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One page of {@code data} from {@code POST /v1/accounts/virtual/list}. */
public record NombaVirtualAccountListPage(
        @JsonProperty("results") List<NombaVirtualAccountDetail> results,
        @JsonProperty("cursor") String cursor
) {}
