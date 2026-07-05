package com.ojo.cyrus.config;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.exception.NombaIntegrationException;
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

@Configuration
@EnableConfigurationProperties(NombaProperties.class)
public class NombaConfig {

    @Bean
    public RestClient nombaRestClient(NombaProperties props, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.timeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // Translate any Nomba error response into a NombaIntegrationException (-> 502) so the raw
                // HttpClientErrorException never leaks out as a generic 500. The provider detail is kept
                // in the exception message (logged with a traceId), not returned to the caller.
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
