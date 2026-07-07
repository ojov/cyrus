package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaBankData;
import com.ojo.cyrus.nomba.dto.NombaBankLookupData;
import com.ojo.cyrus.nomba.dto.NombaBankLookupRequest;
import com.ojo.cyrus.nomba.dto.NombaBankTransferData;
import com.ojo.cyrus.nomba.dto.NombaBankTransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbound bank transfers (merchant payouts) plus the supporting bank lookup/list calls. A payout
 * debits Cyrus's Nomba account and settles to an external bank account; the definitive outcome also
 * arrives asynchronously via a {@code payout_success}/{@code payout_failed} webhook.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaTransferClient {

    private final NombaRestClients restClients;

    /**
     * Initiates a payout. {@code idempotencyKey} (paired with the request's unique {@code merchantTxRef})
     * makes a retry safe — Nomba treats it as the same transfer instead of a second one.
     */
    public NombaBankTransferData transfer(Environment env, NombaBankTransferRequest request, String idempotencyKey) {
        log.info("Initiating Nomba bank transfer ({}) merchantTxRef={}", env, request.merchantTxRef());
        NombaApiResponse<NombaBankTransferData> response = restClients.forEnvironment(env).post()
                .uri(NombaApiUri.BANK_TRANSFER.path())
                .header("X-Idempotent-key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank transfer " + request.merchantTxRef());
    }

    /** Resolves the account holder name for a destination account before a payout. */
    public NombaBankLookupData lookupAccount(Environment env, NombaBankLookupRequest request) {
        NombaApiResponse<NombaBankLookupData> response = restClients.forEnvironment(env).post()
                .uri(NombaApiUri.BANK_LOOKUP.path())
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank account lookup");
    }

    /** The list of payable banks and their NIP codes. */
    public List<NombaBankData> listBanks(Environment env) {
        NombaApiResponse<List<NombaBankData>> response = restClients.forEnvironment(env).get()
                .uri(NombaApiUri.BANK_LIST.path())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank list");
    }
}
