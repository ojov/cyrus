package com.ojo.cyrus.nomba.clients;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.exception.NombaIntegrationException;
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

        // Nomba's transfer endpoint documents a non-"00" code ("201"/PROCESSING, description
        // "PROCESSING") as the NORMAL response for a transfer that was accepted but is still
        // settling — data is `{"status": "PENDING_BILLING"}`, with the caller told to rely on the
        // payout_success/payout_failed webhook for the definitive outcome. The strict "00"-only
        // NombaResponseSupport.requireData (correct for lookup/list, which should only ever succeed
        // with "00") would discard that valid data here and throw — which made PayoutService.initiate
        // treat an ACCEPTED transfer as a provider rejection: refunding the wallet and marking the
        // payout FAILED while the money had genuinely left the account (caught live: a real transfer
        // the recipient received was refunded and marked FAILED, then the later payout_success
        // webhook silently no-opped against the already-terminal FAILED payout). Only throw when
        // there's truly no data to act on; PayoutService.finalizeAccepted already reads the `status`
        // field to decide SUCCESS vs PROCESSING, so PENDING_BILLING correctly becomes PROCESSING
        // (awaiting the webhook) instead of a false FAILED.
        if (response != null && response.data() != null) {
            return response.data();
        }
        String detail = response != null ? response.description() : "null response from Nomba";
        throw new NombaIntegrationException("Nomba bank transfer " + request.merchantTxRef() + " failed: " + detail);
    }

    /**
     * Requeries a transfer (payout) by its Nomba provider transaction reference. Unlike
     * {@link #transfer}, this is a read-only GET and follows the standard "00"-code convention
     * (NombaResponseSupport.requireData is safe here).
     *
     * @param providerReference Nomba's transfer ID, e.g. "API-TRANSFER-XXX"
     * @return transfer data from Nomba (status, amount, fee, etc.)
     * @throws IllegalStateException if no sub-account is configured (the requery endpoint
     *         is only known in its sub-account-scoped variant)
     */
    public NombaBankTransferData requeryTransfer(String providerReference) {
        log.info("Requerying Nomba transfer providerReference={}", providerReference);

        String sub = props.subAccountId();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException(
                    "Transfer requery requires a sub-account (no non-sub-account endpoint is known)");
        }

        NombaApiResponse<NombaBankTransferData> response = nombaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(NombaApiUri.TRANSFER_REQUERY_UNDER_SUBACCOUNT.path())
                        .queryParam("transactionRef", providerReference)
                        .build(sub))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "transfer requery " + providerReference);
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
