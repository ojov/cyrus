package com.ojo.cyrus.nomba.clients;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.NombaApiUri;
import com.ojo.cyrus.nomba.NombaResponseSupport;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaCreateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaExpireVirtualAccountResponse;
import com.ojo.cyrus.nomba.dto.NombaUpdateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaUpdateVirtualAccountResponse;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountDetail;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountFilterRequest;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountListPage;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Virtual-account lifecycle on Cyrus's Nomba account: provision, rename the bank-account holder name,
 * and expire. All calls run against the single {@code nombaRestClient} (base URL + Bearer token +
 * accountId header baked in), so this only supplies paths and bodies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaVirtualAccountClient {

    private final RestClient nombaRestClient;
    private final NombaProperties props;

    /**
     * Provisions a virtual account. The {@code X-Idempotent-key} is deterministic (accountRef), not
     * random: a retry of the same logical create must reuse the key so Nomba treats it as a retry.
     */
    public NombaVirtualAccountData createVirtualAccount(NombaCreateVirtualAccountRequest request) {
        String idempotentKey = CryptoUtil.sha256("va-create:" + request.accountRef());
        log.info("Creating virtual account on Nomba [idempotency: {}]", idempotentKey);

        String sub = props.subAccountId();
        boolean underSub = sub != null && !sub.isBlank();
        String path = underSub ? NombaApiUri.VIRTUAL_ACCOUNT_UNDER_SUBACCOUNT.path() : NombaApiUri.VIRTUAL_ACCOUNT.path();
        Object[] uriVars = underSub ? new Object[]{sub} : new Object[0];

        NombaApiResponse<NombaVirtualAccountData> response = nombaRestClient.post()
                .uri(path, uriVars)
                .header("X-Idempotent-key", idempotentKey)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        NombaVirtualAccountData data = NombaResponseSupport.requireData(response, "virtual account creation");
        log.info("Created virtual account {}", data.bankAccountNumber());
        return data;
    }

    /**
     * Renames the virtual account's bank-account holder name on Nomba's side. {@code accountRef} is
     * the customer's {@code externalCustomerId} used at creation. Only {@code accountName} is sent.
     */
    public void updateVirtualAccountName(String accountRef, String accountName) {
        NombaApiResponse<NombaUpdateVirtualAccountResponse> response = nombaRestClient.put()
                .uri(NombaApiUri.VIRTUAL_ACCOUNT_BY_REF.path(), accountRef)
                .body(new NombaUpdateVirtualAccountRequest(accountName))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        NombaUpdateVirtualAccountResponse data = NombaResponseSupport.requireData(response, "virtual account rename for " + accountRef);
        if (!data.updated()) {
            throw new NombaIntegrationException("Nomba did not confirm rename for account " + accountRef);
        }
        log.info("Renamed virtual account {} to '{}' on Nomba", accountRef, accountName);
    }

    /**
     * Permanently expires a virtual account on Nomba's side. {@code accountRef} is the same value sent
     * at creation (the customer's {@code externalCustomerId}). Irreversible.
     *
     * @see #suspendVirtualAccount(String) for the reversible counterpart.
     */
    public void expireVirtualAccount(String accountRef) {
        NombaApiResponse<NombaExpireVirtualAccountResponse> response = nombaRestClient.delete()
                .uri(NombaApiUri.VIRTUAL_ACCOUNT_BY_REF.path(), accountRef)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        NombaExpireVirtualAccountResponse data = NombaResponseSupport.requireData(response, "virtual account expiry for " + accountRef);
        if (!data.expired()) {
            throw new NombaIntegrationException("Nomba did not confirm expiry for account " + accountRef);
        }
        log.info("Expired virtual account {} on Nomba", accountRef);
    }

    /**
     * Fetches a virtual account's live state directly from Nomba, bypassing Cyrus's own
     * {@code VirtualAccount} table entirely. Not used by any request-serving path today — Cyrus's
     * local record is authoritative for normal operation — but useful for an admin spot-check
     * ("what does Nomba actually think this VA's status is right now") or a drift audit.
     */
    public NombaVirtualAccountDetail getVirtualAccount(String accountRef) {
        NombaApiResponse<NombaVirtualAccountDetail> response = nombaRestClient.get()
                .uri(NombaApiUri.VIRTUAL_ACCOUNT_BY_REF.path(), accountRef)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "virtual account fetch for " + accountRef);
    }

    /**
     * Lists/filters virtual accounts directly from Nomba. Same "not on any request-serving path"
     * caveat as {@link #getVirtualAccount(String)} — this exists for admin tooling (e.g. auditing
     * Cyrus's local {@code VirtualAccount} table against what Nomba actually has, to catch a VA that
     * leaked on Nomba's side without a matching local row).
     */
    public NombaVirtualAccountListPage listVirtualAccounts(NombaVirtualAccountFilterRequest filter, int limit, String cursor) {
        NombaApiResponse<NombaVirtualAccountListPage> response = nombaRestClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path(NombaApiUri.VIRTUAL_ACCOUNT_LIST.path())
                            .queryParam("limit", limit);
                    if (cursor != null && !cursor.isBlank()) {
                        uriBuilder.queryParam("cursor", cursor);
                    }
                    return uriBuilder.build();
                })
                .body(filter)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "virtual account list");
    }

    /**
     * Suspends (or unsuspends) a virtual account on Nomba's side. {@code accountRef} is the same
     * value sent at creation (the customer's {@code externalCustomerId}). Reversible — call this
     * again with the same value to reactivate.
     *
     * <p><strong>Not verified during the hackathon:</strong> Nomba disabled the suspend endpoint
     * on our provisioned account (all identifier formats returned 403 "Forbidden"). The code and
     * URI are correct per Nomba's published docs; once enabled by Nomba, this should work with
     * no changes.
     */
    public void suspendVirtualAccount(String accountRef) {
        NombaApiResponse<Boolean> response = nombaRestClient.put()
                .uri(NombaApiUri.SUSPEND_VIRTUAL_ACCOUNT.path(), accountRef)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        Boolean data = NombaResponseSupport.requireData(response, "virtual account suspend for " + accountRef);
        if (!Boolean.TRUE.equals(data)) {
            throw new NombaIntegrationException("Nomba did not confirm suspend for account " + accountRef);
        }
        log.info("Suspended virtual account {} on Nomba", accountRef);
    }
}
