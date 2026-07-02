package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaRefreshTokenRequest;
import com.ojo.cyrus.nomba.dto.NombaTokenData;
import com.ojo.cyrus.nomba.dto.NombaTokenRequest;
import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class NombaAuthenticationService {

    private final RestClient nombaRestClient;
    private final AppProperties appProperties;

    private final ConcurrentHashMap<String, NombaTokenEntry> tokenCache = new ConcurrentHashMap<>();

    public String getAccessToken(NombaCredentials creds, Environment env) {
        String cacheKey = creds.cacheKey() + ":" + env.name();
        NombaTokenEntry entry = tokenCache.compute(cacheKey, (k, existing) -> {
            if (existing != null && existing.isValid()) {
                log.debug("Using cached Nomba token for {} env {}", creds.cacheKey(), env);
                return existing;
            }
            if (existing != null && existing.refreshToken() != null) {
                try {
                    log.info("Refreshing Nomba token for {} env {}", creds.cacheKey(), env);
                    return refreshToken(creds, env, existing);
                } catch (Exception e) {
                    log.warn("Token refresh failed for {} env {}, re-authenticating: {}",
                            creds.cacheKey(), env, e.getMessage());
                }
            }
            log.info("Authenticating with Nomba for {} env {}", creds.cacheKey(), env);
            return authenticate(creds, env);
        });
        return entry.accessToken();
    }

    private NombaTokenEntry refreshToken(NombaCredentials creds, Environment env, NombaTokenEntry existing) {
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaTokenData> response = nombaRestClient.post()
                .uri(baseUrl + "/v1/auth/token/refresh")
                .header("accountId", creds.parentAccountId())
                .header("Authorization", "Bearer " + existing.accessToken())
                .body(new NombaRefreshTokenRequest("refresh_token", existing.refreshToken()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            throw new NombaIntegrationException("Token refresh failed: " +
                    (response != null ? response.description() : "null response"));
        }

        NombaTokenData data = response.data();
        log.info("Refreshed Nomba token for {} env {}, expires at {}", creds.cacheKey(), env, data.expiresAt());
        return new NombaTokenEntry(data.accessToken(), data.refreshToken(), data.expiresAt());
    }

    private NombaTokenEntry authenticate(NombaCredentials creds, Environment env) {
        NombaCredential credential = creds.credentials().get(env);
        if (credential == null) {
            throw new NombaIntegrationException(
                    "No Nomba credentials configured for environment " + env + ". " +
                    "Please add your " + env.name().toLowerCase() + " Nomba credentials.");
        }

        String decryptedSecret = CryptoUtil.decrypt(credential.encryptedClientSecret(), appProperties.encryptionKey());
        String baseUrl = Provider.NOMBA.getBaseUrl(env);

        NombaApiResponse<NombaTokenData> response = nombaRestClient.post()
                .uri(baseUrl + "/v1/auth/token/issue")
                .header("accountId", creds.parentAccountId())
                .body(new NombaTokenRequest("client_credentials", credential.clientId(), decryptedSecret))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess()) {
            String msg = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Nomba authentication failed: " + msg);
        }

        NombaTokenData data = response.data();
        log.info("Authenticated with Nomba for {} env {}, token expires at {}",
                creds.cacheKey(), env, data.expiresAt());
        return new NombaTokenEntry(data.accessToken(), data.refreshToken(), data.expiresAt());
    }

    public void evictToken(String cacheKey, Environment env) {
        tokenCache.remove(cacheKey + ":" + env.name());
    }
}
