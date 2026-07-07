package com.ojo.cyrus.nomba;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaCreateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaExpireVirtualAccountResponse;
import com.ojo.cyrus.nomba.dto.NombaUpdateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaUpdateVirtualAccountResponse;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
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
}
