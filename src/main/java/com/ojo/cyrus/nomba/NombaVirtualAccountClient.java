package com.ojo.cyrus.nomba;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.enums.Environment;
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

/**
 * Virtual-account lifecycle on Cyrus's Nomba account: provision, rename the bank-account holder name,
 * and expire. All calls run against the per-environment {@link NombaRestClients} client (base URL +
 * Bearer token + accountId header are baked in), so this only supplies paths and bodies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaVirtualAccountClient {

    private final NombaRestClients restClients;
    private final NombaProperties props;

    /**
     * Provisions a virtual account. The {@code X-Idempotent-key} is deterministic (env + accountRef),
     * not random: a retry of the same logical create must reuse the key so Nomba treats it as a retry
     * instead of provisioning a second account for the same customer.
     */
    public NombaVirtualAccountData createVirtualAccount(Environment env, NombaCreateVirtualAccountRequest request) {
        String idempotentKey = CryptoUtil.sha256("va-create:" + env + ":" + request.accountRef());
        log.info("Creating virtual account on Nomba ({}) [idempotency: {}]", env, idempotentKey);

        String sub = props.subAccountId();
        boolean underSub = sub != null && !sub.isBlank();
        String path = underSub ? NombaApiUri.VIRTUAL_ACCOUNT_UNDER_SUBACCOUNT.path() : NombaApiUri.VIRTUAL_ACCOUNT.path();
        Object[] uriVars = underSub ? new Object[]{sub} : new Object[0];

        NombaApiResponse<NombaVirtualAccountData> response = restClients.forEnvironment(env).post()
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
     * the customer's {@code externalCustomerId} used at creation. Only {@code accountName} is sent
     * (see {@link NombaUpdateVirtualAccountRequest}).
     */
    public void updateVirtualAccountName(Environment env, String accountRef, String accountName) {
        NombaApiResponse<NombaUpdateVirtualAccountResponse> response = restClients.forEnvironment(env).put()
                .uri(NombaApiUri.VIRTUAL_ACCOUNT_BY_REF.path(), accountRef)
                .body(new NombaUpdateVirtualAccountRequest(accountName))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        NombaUpdateVirtualAccountResponse data = NombaResponseSupport.requireData(response, "virtual account rename for " + accountRef);
        if (!data.updated()) {
            throw new NombaIntegrationException("Nomba did not confirm rename for account " + accountRef);
        }
        log.info("Renamed virtual account {} to '{}' on Nomba ({})", accountRef, accountName, env);
    }

    /**
     * Permanently expires a virtual account on Nomba's side. {@code accountRef} is the same value sent
     * at creation (the customer's {@code externalCustomerId}). Irreversible — call only when closing a
     * customer for good, not for a temporary suspension.
     */
    public void expireVirtualAccount(Environment env, String accountRef) {
        NombaApiResponse<NombaExpireVirtualAccountResponse> response = restClients.forEnvironment(env).delete()
                .uri(NombaApiUri.VIRTUAL_ACCOUNT_BY_REF.path(), accountRef)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        NombaExpireVirtualAccountResponse data = NombaResponseSupport.requireData(response, "virtual account expiry for " + accountRef);
        if (!data.expired()) {
            throw new NombaIntegrationException("Nomba did not confirm expiry for account " + accountRef);
        }
        log.info("Expired virtual account {} on Nomba ({})", accountRef, env);
    }
}
