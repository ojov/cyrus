package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.dto.*;
import com.ojo.cyrus.nomba.service.NombaAuthenticationService;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class NombaClient {

    private final RestClient nombaRestClient;
    private final NombaAuthenticationService authService;

    public NombaVirtualAccountData createVirtualAccount(NombaCredentials creds,
                                                        NombaCreateVirtualAccountRequest request,
                                                        Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);
        String path = resolveVirtualAccountPath(creds, baseUrl);
        // Deterministic, not random: a retry of the same logical create (e.g. the developer's
        // client retries POST /v1/customers after losing the response to a network blip) must
        // reuse the same key so Nomba recognizes it as a retry instead of provisioning a second
        // virtual account for the same customer.
        String idempotentKey = CryptoUtil.sha256("va-create:" + creds.cacheKey() + ":" + request.accountRef());

        log.info("Creating virtual account on Nomba via {} [idempotency: {}]", path, idempotentKey);

        NombaApiResponse<NombaVirtualAccountData> response = nombaRestClient.post()
                .uri(path)
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotent-key", idempotentKey)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            log.error("Nomba VA creation failed: {}", msg);
            throw new NombaIntegrationException("Virtual account creation failed: " + msg);
        }

        log.info("Created virtual account {}", response.data().bankAccountNumber());
        return response.data();
    }

    public NombaBalanceData getSubAccountBalance(NombaCredentials creds, String subAccountId, Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaBalanceData> response = nombaRestClient.get()
                .uri(baseUrl + "/v1/accounts/" + subAccountId + "/balance")
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Failed to fetch balance for sub-account " + subAccountId + ": " + msg);
        }

        return response.data();
    }

    public NombaBalanceData getParentAccountBalance(NombaCredentials creds, Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaBalanceData> response = nombaRestClient.get()
                .uri(baseUrl + "/v1/accounts/parent/balance")
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Failed to fetch parent account balance: " + msg);
        }

        return response.data();
    }

    /** Reconciliation entry point: asks Nomba directly what it knows about a transfer session. */
    public NombaTransactionData requeryTransaction(NombaCredentials creds, String sessionId, Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaTransactionData> response = nombaRestClient.get()
                .uri(baseUrl + "/v1/transactions/requery/" + sessionId)
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Failed to requery transaction " + sessionId + ": " + msg);
        }

        return response.data();
    }

    /**
     * Renames the virtual account's bank account holder name on Nomba's side (the name a payer
     * sees before sending a transfer). {@code accountRef} is the same {@code Customer.reference}
     * used at creation/expiry. Only {@code accountName} is sent — Nomba's schema also accepts
     * {@code newAccountRef}/{@code callbackUrl}/{@code expectedAmount}, which this deliberately
     * never touches (see {@link NombaUpdateVirtualAccountRequest}).
     */
    public void updateVirtualAccountName(NombaCredentials creds, String accountRef, String accountName, Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaUpdateVirtualAccountResponse> response = nombaRestClient.put()
                .uri(baseUrl + "/v1/accounts/virtual/" + accountRef)
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .body(new NombaUpdateVirtualAccountRequest(accountName))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess() || response.data() == null || !response.data().updated()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            log.error("Nomba VA rename failed for accountRef={}: {}", accountRef, msg);
            throw new NombaIntegrationException("Failed to update virtual account name for " + accountRef + ": " + msg);
        }

        log.info("Renamed virtual account {} to '{}' on Nomba", accountRef, accountName);
    }

    /**
     * Permanently expires a virtual account on Nomba's side. {@code accountRef} is the SAME value
     * passed as {@code accountRef} at creation time ({@link #createVirtualAccount}) — Nomba echoes
     * it back on every webhook as {@code aliasAccountReference}, and Cyrus stores it as
     * {@code Customer.reference}. Irreversible: call only when closing a customer for good, not for
     * a temporary suspension (Cyrus tracks suspension locally without touching Nomba).
     */
    public void expireVirtualAccount(NombaCredentials creds, String accountRef, Environment env) {
        String accessToken = authService.getAccessToken(creds, env);
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<Object> response = nombaRestClient.delete()
                .uri(baseUrl + "/v1/accounts/virtual/" + accountRef)
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            log.error("Nomba VA expiry failed for accountRef={}: {}", accountRef, msg);
            throw new NombaIntegrationException("Failed to expire virtual account " + accountRef + ": " + msg);
        }

        log.info("Expired virtual account {} on Nomba", accountRef);
    }

    private String resolveVirtualAccountPath(NombaCredentials creds, String baseUrl) {
        return creds.subAccountIds().stream()
                .findFirst()
                .map(subId -> baseUrl + "/v1/accounts/virtual/" + subId)
                .orElse(baseUrl + "/v1/accounts/virtual");
    }
}
