package com.ojo.cyrus.config;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.NombaAuthProvider;
import com.ojo.cyrus.nomba.NombaRestClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds one {@link RestClient} per {@link Environment} for Cyrus's single platform Nomba account,
 * the "Spring Boot 4 way": base URL, timeouts, and auth are baked into the client so the thin Nomba
 * clients issue plain relative-path calls. A request interceptor injects a fresh Bearer token (from
 * {@link NombaAuthProvider}) and the {@code accountId} header on every request; a status handler
 * translates any Nomba error into a {@link NombaIntegrationException} (→ 502) so a raw
 * {@code HttpClientErrorException} never leaks out as a generic 500.
 */
@Configuration
@EnableConfigurationProperties(NombaProperties.class)
public class NombaConfig {

    @Bean
    public NombaRestClients nombaRestClients(NombaProperties props, NombaAuthProvider authProvider,
                                             ObjectMapper objectMapper) {
        Map<Environment, RestClient> byEnv = new EnumMap<>(Environment.class);
        for (Environment env : Environment.values()) {
            byEnv.put(env, buildClient(env, props, authProvider, objectMapper));
        }
        return new NombaRestClients(byEnv);
    }

    private RestClient buildClient(Environment env, NombaProperties props, NombaAuthProvider authProvider,
                                   ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.timeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));

        String accountId = props.accountId();

        return RestClient.builder()
                .baseUrl(props.baseUrl(env))
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(authProvider.getAccessToken(env));
                    if (accountId != null) {
                        request.getHeaders().add("accountId", accountId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new NombaIntegrationException(describeError(objectMapper, response.getStatusCode(), body));
                })
                .build();
    }

    private static String describeError(ObjectMapper mapper, HttpStatusCode status, String body) {
        String detail = (body == null || body.isBlank()) ? "no response body" : body;
        try {
            JsonNode node = mapper.readTree(body);
            if (node.hasNonNull("description")) {
                detail = node.get("description").asText();
            } else if (node.hasNonNull("message")) {
                detail = node.get("message").asText();
            }
        } catch (Exception ignore) {
            // not JSON — fall back to the raw body
        }
        return "Nomba API error (HTTP " + status.value() + "): " + detail;
    }
}
