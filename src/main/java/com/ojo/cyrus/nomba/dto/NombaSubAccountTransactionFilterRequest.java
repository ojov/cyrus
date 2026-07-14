package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional body filters for {@code POST /v1/transactions/accounts/{subAccountId}}
 * ({@link com.ojo.cyrus.nomba.NombaApiUri#SUBACCOUNT_TRANSACTIONS_FILTER}) — date range and
 * pagination are query params, these are additional narrowing filters. All null (an empty body) asks
 * for everything in the window, which is what {@link com.ojo.cyrus.services.MissingWebhookSweepService}
 * sends until the {@code type} value that denotes an inbound VA credit is confirmed against a real
 * response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NombaSubAccountTransactionFilterRequest(
        @JsonProperty("transactionRef") String transactionRef,
        @JsonProperty("status") String status,
        @JsonProperty("source") String source,
        @JsonProperty("type") String type,
        @JsonProperty("terminalId") String terminalId,
        @JsonProperty("rrn") String rrn,
        @JsonProperty("merchantTxRef") String merchantTxRef,
        @JsonProperty("orderReference") String orderReference,
        @JsonProperty("orderId") String orderId
) {
    public static NombaSubAccountTransactionFilterRequest empty() {
        return new NombaSubAccountTransactionFilterRequest(null, null, null, null, null, null, null, null, null);
    }
}
