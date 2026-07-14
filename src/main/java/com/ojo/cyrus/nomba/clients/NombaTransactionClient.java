package com.ojo.cyrus.nomba.clients;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.nomba.NombaApiUri;
import com.ojo.cyrus.nomba.NombaResponseSupport;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaSubAccountTransactionFilterRequest;
import com.ojo.cyrus.nomba.dto.NombaSubAccountTransactionPage;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Reconciliation entry point: asks Nomba directly what it knows about a transfer session. Nomba is
 * treated as the source of truth — reconciliation requeries this to confirm/settle a transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaTransactionClient {

    // dateFrom/dateTo are sent as zone-less ISO local date-times of the UTC instant (e.g.
    // "2026-07-14T13:58:39") — the shape Nomba's docs show. Verified live: a transaction Nomba
    // timestamps at 13:32:51Z was correctly returned within a window ending 13:58:39, confirming
    // Nomba interprets these as UTC (its own timestamps are UTC "...Z" too).
    private static final DateTimeFormatter QUERY_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestClient nombaRestClient;
    private final NombaProperties props;

    public NombaTransactionData requeryTransaction(String sessionId) {
        NombaApiResponse<NombaTransactionData> response = nombaRestClient.get()
                .uri(NombaApiUri.TRANSACTION_REQUERY.path(), sessionId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "transaction requery " + sessionId);
    }

    /**
     * One page of the sub-account's transaction list in {@code [from, to)}, {@code limit} items,
     * continuing from {@code cursor} (null for the first page). See
     * {@link NombaApiUri#SUBACCOUNT_TRANSACTIONS_FILTER} for what is/isn't confirmed about this
     * endpoint's response shape.
     *
     * @throws IllegalStateException if no sub-account is configured — the endpoint is only known in
     *         its sub-account-scoped form, same constraint as {@link NombaTransferClient#requeryTransfer}
     */
    public NombaSubAccountTransactionPage listSubAccountTransactions(Instant from, Instant to, int limit, String cursor) {
        String sub = props.subAccountId();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException(
                    "Sub-account transaction listing requires a sub-account (no non-sub-account endpoint is known)");
        }

        String dateFrom = QUERY_DATE_FORMAT.format(from.atZone(ZoneOffset.UTC));
        String dateTo = QUERY_DATE_FORMAT.format(to.atZone(ZoneOffset.UTC));

        NombaApiResponse<NombaSubAccountTransactionPage> response = nombaRestClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path(NombaApiUri.SUBACCOUNT_TRANSACTIONS_FILTER.path())
                            .queryParam("dateFrom", dateFrom)
                            .queryParam("dateTo", dateTo)
                            .queryParam("limit", limit);
                    if (cursor != null && !cursor.isBlank()) {
                        uriBuilder.queryParam("cursor", cursor);
                    }
                    return uriBuilder.build(sub);
                })
                .body(NombaSubAccountTransactionFilterRequest.empty())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "sub-account transaction list");
    }
}
