package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * One page of {@code data} from {@code POST /v1/transactions/accounts/{subAccountId}}
 * ({@link com.ojo.cyrus.nomba.NombaApiUri#SUBACCOUNT_TRANSACTIONS_FILTER}). The item schema is
 * verified against real responses (a VA credit carries {@code recipientAccountNumber},
 * {@code virtualAccountReference}, {@code sessionId}, {@code entryType}, {@code amount},
 * {@code fixedCharge}, {@code status}); {@code results} is kept as raw {@link JsonNode}s rather than a
 * narrow record so any rare/extra field Nomba may send isn't silently dropped and the sweep can read
 * whichever fields it needs.
 */
public record NombaSubAccountTransactionPage(
        @JsonProperty("results") List<JsonNode> results,
        @JsonProperty("cursor") String cursor
) {}
