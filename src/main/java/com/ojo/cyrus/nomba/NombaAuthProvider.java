package com.ojo.cyrus.nomba;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaTokenData;
import com.ojo.cyrus.nomba.dto.NombaTokenEntry;
import com.ojo.cyrus.nomba.dto.NombaTokenRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acquires and caches Nomba OAuth access tokens for Cyrus's own platform account, one token per
 * {@link Environment} (client-credentials grant). Modeled on the gbgapi {@code AuthProvider}: a
 * proactively-refreshed cache (re-authenticates ~5 min before the 30-minute token expires).
 *
 * <p>Deliberately uses its <em>own</em> bare {@code RestClient} for the token endpoint rather than
 * the auth-injecting {@code NombaRestClients} beans — otherwise the interceptor that asks this
 * provider for a token would recurse.
 */
@Service
@Slf4j
public class NombaAuthProvider {

    private final NombaProperties props;
    private final RestClient authRestClient;
    private final ConcurrentHashMap<Environment, NombaTokenEntry> cache = new ConcurrentHashMap<>();

    public NombaAuthProvider(NombaProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        this.authRestClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** A valid access token for the given environment, authenticating or reusing the cache as needed. */
    public String getAccessToken(Environment env) {
        NombaTokenEntry entry = cache.compute(env, (k, existing) -> {
            if (existing != null && existing.isValid()) {
                return existing;
            }
            log.info("Authenticating with Nomba ({})", env);
            return authenticate(env);
        });
        return entry.accessToken();
    }

    /** Drops the cached token for an environment, forcing re-authentication on next use. */
    public void evict(Environment env) {
        cache.remove(env);
    }

    private NombaTokenEntry authenticate(Environment env) {
        NombaProperties.Credentials creds = props.credentials(env);
        if (creds == null || creds.clientId() == null || creds.clientSecret() == null) {
            throw new NombaIntegrationException(
                    "No Nomba platform credentials configured for environment " + env);
        }

        NombaApiResponse<NombaTokenData> response = authRestClient.post()
                .uri(props.baseUrl(env) + NombaApiUri.TOKEN_ISSUE.path())
                .header("accountId", props.accountId())
                .body(new NombaTokenRequest("client_credentials", creds.clientId(), creds.clientSecret()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess() || response.data() == null) {
            String detail = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Nomba authentication failed for " + env + ": " + detail);
        }

        NombaTokenData data = response.data();
        log.info("Authenticated with Nomba ({}); token expires at {}", env, data.expiresAt());
        return new NombaTokenEntry(data.accessToken(), data.refreshToken(), data.expiresAt());
    }
}
