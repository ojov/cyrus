package com.ojo.cyrus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP client for delivering outbound webhooks to merchant-registered URLs. Deliberately separate
 * from {@code nombaRestClient}: this one keeps RestClient's DEFAULT status handling (which throws
 * {@code RestClientResponseException} on 4xx/5xx) so a merchant endpoint returning an error is
 * caught by the dispatcher and recorded as a retryable delivery failure — NOT rethrown as a
 * {@code NombaIntegrationException} the way the Nomba client's custom handler would.
 */
@Configuration
public class WebhookHttpConfig {

    // Merchant endpoints are untrusted third parties — keep timeouts tight so a slow/hanging
    // endpoint doesn't tie up a JobRunr worker thread; the failure just retries with backoff.
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient merchantWebhookRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(TIMEOUT);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
