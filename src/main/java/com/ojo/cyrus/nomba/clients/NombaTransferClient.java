package com.ojo.cyrus.nomba.clients;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.nomba.NombaApiUri;
import com.ojo.cyrus.nomba.NombaResponseSupport;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaBankData;
import com.ojo.cyrus.nomba.dto.NombaBankLookupData;
import com.ojo.cyrus.nomba.dto.NombaBankLookupRequest;
import com.ojo.cyrus.nomba.dto.NombaBankTransferData;
import com.ojo.cyrus.nomba.dto.NombaBankTransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private final RestClient nombaRestClient;
    private final NombaProperties props;

    /**
     * Initiates a payout. {@code idempotencyKey} (paired with the request's unique {@code merchantTxRef})
     * makes a retry safe — Nomba treats it as the same transfer instead of a second one. When a
     * sub-account is configured the transfer targets the {@code {subAccountId}} path variant (Cyrus
     * transacts under a sub-account), mirroring {@code NombaVirtualAccountClient.createVirtualAccount}.
     */
    public NombaBankTransferData transfer(NombaBankTransferRequest request, String idempotencyKey) {
        log.info("Initiating Nomba bank transfer merchantTxRef={}", request.merchantTxRef());

        String sub = props.subAccountId();
        boolean underSub = sub != null && !sub.isBlank();
        String path = underSub ? NombaApiUri.BANK_TRANSFER_UNDER_SUBACCOUNT.path() : NombaApiUri.BANK_TRANSFER.path();
        Object[] uriVars = underSub ? new Object[]{sub} : new Object[0];

        NombaApiResponse<NombaBankTransferData> response = nombaRestClient.post()
                .uri(path, uriVars)
                .header("X-Idempotent-key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank transfer " + request.merchantTxRef());
    }

    /** Resolves the account holder name for a destination account before a payout. */
    public NombaBankLookupData lookupAccount(NombaBankLookupRequest request) {
        NombaApiResponse<NombaBankLookupData> response = nombaRestClient.post()
                .uri(NombaApiUri.BANK_LOOKUP.path())
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank account lookup");
    }

    /**
     * The list of payable banks and their NIP codes. Cached ({@code nombaBanks}) — the list is
     * effectively static, so this hits Nomba once and serves every later "add a beneficiary" bank
     * picker from memory. {@code @Cacheable} never caches a thrown exception, so a transient Nomba
     * failure isn't cached; the next call re-hits Nomba and caches on success.
     */
    @Cacheable("nombaBanks")
    public List<NombaBankData> listBanks() {
        NombaApiResponse<List<NombaBankData>> response = nombaRestClient.get()
                .uri(NombaApiUri.BANK_LIST.path())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "bank list");
    }
}
