package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.nomba.dto.NombaCreateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
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

        log.info("Creating virtual account on Nomba via {}", path);

        NombaApiResponse<NombaVirtualAccountData> response = nombaRestClient.post()
                .uri(path)
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + accessToken)
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

    private String resolveVirtualAccountPath(NombaCredentials creds, String baseUrl) {
        return creds.subAccountIds().stream()
                .findFirst()
                .map(subId -> baseUrl + "/v1/accounts/virtual/" + subId)
                .orElse(baseUrl + "/v1/accounts/virtual");
    }
}
