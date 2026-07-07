package com.ojo.cyrus.nomba;

import com.ojo.cyrus.config.properties.NombaProperties;
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

/**
 * Acquires and caches the Nomba OAuth access token for Cyrus's platform account (client-credentials
 * grant). Modeled on the gbgapi {@code AuthProvider}: a proactively-refreshed token (re-authenticates
 * ~5 min before the 30-minute expiry), guarded by {@code synchronized}.
 *
 * <p>Deliberately uses its <em>own</em> bare {@code RestClient} for the token endpoint rather than the
 * auth-injecting {@code nombaRestClient} bean — otherwise the interceptor that asks this provider for
 * a token would recurse.
 */
@Service
@Slf4j
public class NombaAuthProvider {

    private final NombaProperties props;
    private final RestClient authRestClient;
    private volatile NombaTokenEntry token;

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

    /** A valid access token, authenticating or reusing the cached one as needed. */
    public synchronized String getAccessToken() {
        if (token == null || !token.isValid()) {
            log.info("Authenticating with Nomba");
            token = authenticate();
        }
        return token.accessToken();
    }

    /** Drops the cached token, forcing re-authentication on next use. */
    public synchronized void evict() {
        token = null;
    }

    private NombaTokenEntry authenticate() {
        if (props.clientId() == null || props.clientSecret() == null) {
            throw new NombaIntegrationException("No Nomba platform credentials configured");
        }

        NombaApiResponse<NombaTokenData> response = authRestClient.post()
                .uri(props.baseUrl() + NombaApiUri.TOKEN_ISSUE.path())
                .header("accountId", props.accountId())
                .body(new NombaTokenRequest("client_credentials", props.clientId(), props.clientSecret()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.isSuccess() || response.data() == null) {
            String detail = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Nomba authentication failed: " + detail);
        }

        NombaTokenData data = response.data();
        log.info("Authenticated with Nomba; token expires at {}", data.expiresAt());
        return new NombaTokenEntry(data.accessToken(), data.refreshToken(), data.expiresAt());
    }
}
