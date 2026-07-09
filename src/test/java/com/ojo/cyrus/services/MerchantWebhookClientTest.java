package com.ojo.cyrus.services;

import com.ojo.cyrus.models.dto.DeliveryOutcome;
import com.ojo.cyrus.utils.CryptoUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link MerchantWebhookClient} against a real local HTTP server (JDK's built-in
 * {@link HttpServer}, no mocking framework needed) — verifies the actual bytes on the wire: the
 * HMAC signature, the headers, and how each response class maps to a {@link DeliveryOutcome}. This
 * is the logic a bug in would silently break every merchant's webhook signature verification, so
 * it's tested against a real HTTP round trip rather than a mocked RestClient.
 */
class MerchantWebhookClientTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD = "{\"event\":\"payment.succeeded\",\"data\":{\"amountKobo\":15000}}";

    private HttpServer server;
    private MerchantWebhookClient client;

    private void startServer(int statusCode, byte[] responseBody, AtomicReference<HttpExchange> capturedExchange) throws IOException {
        int port = findFreePort();
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/webhook", exchange -> {
            capturedExchange.set(exchange);
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        client = new MerchantWebhookClient(RestClient.builder().build());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void success_signsCorrectlyAndReturnsDeliveryOutcomeSuccess() throws IOException, InterruptedException {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer(200, new byte[0], captured);

        UUID deliveryId = UUID.randomUUID();
        DeliveryOutcome outcome = client.send(
                "http://localhost:" + server.getAddress().getPort() + "/webhook",
                "payment.succeeded", deliveryId, PAYLOAD, SECRET);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(200);
        assertThat(outcome.retryable()).isFalse();

        HttpExchange exchange = captured.get();
        assertThat(exchange.getRequestHeaders().getFirst("X-Cyrus-Event")).isEqualTo("payment.succeeded");
        assertThat(exchange.getRequestHeaders().getFirst("X-Cyrus-Delivery")).isEqualTo(deliveryId.toString());

        String timestamp = exchange.getRequestHeaders().getFirst("X-Cyrus-Timestamp");
        String signature = exchange.getRequestHeaders().getFirst("X-Cyrus-Signature");
        assertThat(timestamp).isNotBlank();
        // Recompute independently from the raw received timestamp + secret — proves the signature
        // genuinely covers "timestamp + '.' + payload" and isn't, say, the payload alone (which
        // would let a captured signature be replayed with a forged timestamp).
        String expected = "sha256=" + CryptoUtil.hmacSha256(timestamp + "." + PAYLOAD, SECRET);
        assertThat(signature).isEqualTo(expected);
    }

    @Test
    void serverError_isRetryable() throws IOException {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer(500, "boom".getBytes(StandardCharsets.UTF_8), captured);

        DeliveryOutcome outcome = client.send(
                "http://localhost:" + server.getAddress().getPort() + "/webhook",
                "payment.succeeded", UUID.randomUUID(), PAYLOAD, SECRET);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(500);
    }

    @Test
    void tooManyRequests_isRetryable() throws IOException {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer(429, new byte[0], captured);

        DeliveryOutcome outcome = client.send(
                "http://localhost:" + server.getAddress().getPort() + "/webhook",
                "payment.succeeded", UUID.randomUUID(), PAYLOAD, SECRET);

        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(429);
    }

    @Test
    void notFound_isPermanentFailure_notRetried() throws IOException {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer(404, new byte[0], captured);

        DeliveryOutcome outcome = client.send(
                "http://localhost:" + server.getAddress().getPort() + "/webhook",
                "payment.succeeded", UUID.randomUUID(), PAYLOAD, SECRET);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.statusCode()).isEqualTo(404);
    }

    @Test
    void unauthorized_isPermanentFailure_notRetried() throws IOException {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer(401, new byte[0], captured);

        DeliveryOutcome outcome = client.send(
                "http://localhost:" + server.getAddress().getPort() + "/webhook",
                "payment.succeeded", UUID.randomUUID(), PAYLOAD, SECRET);

        assertThat(outcome.retryable()).isFalse();
    }

    @Test
    void connectionFailure_isRetryable() {
        // Nothing listening on this port — a genuine connection-refused, the same failure class as
        // a merchant's endpoint being down or DNS not resolving.
        client = new MerchantWebhookClient(RestClient.builder().build());

        DeliveryOutcome outcome = client.send(
                "http://localhost:1/webhook", "payment.succeeded", UUID.randomUUID(), PAYLOAD, SECRET);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
    }
}
