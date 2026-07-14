package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * One page of {@code data} from {@code POST /v1/transactions/accounts/{subAccountId}}
 * ({@link com.ojo.cyrus.nomba.NombaApiUri#SUBACCOUNT_TRANSACTIONS_FILTER}). {@code results} is kept
 * as raw {@link JsonNode}s rather than bound to a strict item DTO — the item schema beyond
 * {@code id}/{@code status}/{@code amount}/{@code type}/{@code source}/{@code timeCreated} is not
 * confirmed against a real response, so binding to a narrow record would silently drop whatever field
 * actually carries the destination virtual-account number.
 */
public record NombaSubAccountTransactionPage(
        @JsonProperty("results") List<JsonNode> results,
        @JsonProperty("cursor") String cursor
) {}
