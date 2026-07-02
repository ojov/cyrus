//package com.ojo.cyrus.services;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.ojo.cyrus.models.NombaCredential;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestClientException;
//import org.springframework.web.client.RestTemplate;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.Base64;
//import java.util.HashMap;
//import java.util.HexFormat;
//import java.util.List;
//import java.util.Map;
//
///**
// * Nomba API Client
// *
// * Handles communication with Nomba's virtual account and transaction APIs.
// * Manages authentication, virtual account provisioning, transaction queries,
// * and webhook signature verification.
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class NombaClient {
//
//    private final RestTemplate restTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Value("${nomba.api.base-url:https://api.nomba.com}")
//    private String nombaBaseUrl;
//
//    @Value("${nomba.api.timeout:30000}")
//    private int requestTimeout;
//
//    /**
//     * Authenticate with Nomba using client credentials.
//     * Returns an access token for subsequent API calls.
//     */
//    public String authenticate(String clientId, String clientSecret) {
//        try {
//            log.info("Authenticating with Nomba (clientId: {})", clientId);
//
//            String url = nombaBaseUrl + "/auth/token";
//
//            // Encode credentials in Basic Auth format
//            String credentials = clientId + ":" + clientSecret;
//            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
//
//            Map<String, String> headers = new HashMap<>();
//            headers.put("Authorization", "Basic " + encodedCredentials);
//            headers.put("Content-Type", "application/x-www-form-urlencoded");
//
//            // TODO: Implement actual HTTP call using RestTemplate
//            // For now, this is a placeholder
//            log.debug("Nomba authentication endpoint: {}", url);
//
//            // This should call Nomba API and return a token
//            // return response.getBody().getAccessToken();
//
//            throw new RuntimeException("TODO: Implement Nomba authentication");
//
//        } catch (Exception e) {
//            log.error("Nomba authentication failed", e);
//            throw new RuntimeException("Failed to authenticate with Nomba", e);
//        }
//    }
//
//    /**
//     * Provision a virtual account for a customer.
//     *
//     * @param credential Merchant's Nomba credentials
//     * @param customerName Display name for the account
//     * @param customerEmail Customer's email address (optional)
//     * @return Virtual account details including account number (NUBAN)
//     */
//    public NombaVirtualAccountResponse createVirtualAccount(
//            NombaCredential credential,
//            String customerName,
//            String customerEmail) {
//
//        try {
//            log.info("Provisioning virtual account for customer: {}", customerName);
//
//            // Step 1: Get access token
//            // TODO: decrypt credential.encryptedClientSecret() before use
//            String accessToken = authenticate(credential.clientId(), credential.encryptedClientSecret());
//
//            // Step 2: Call Nomba VA creation endpoint
//            String url = nombaBaseUrl + "/accounts/virtual-accounts";
//
//            Map<String, Object> requestBody = new HashMap<>();
//            requestBody.put("account_name", customerName);
//            requestBody.put("account_email", customerEmail);
//            requestBody.put("customer_name", customerName);
//
//            log.debug("Creating VA on Nomba: {}", requestBody);
//
//            // TODO: Make HTTP POST request with Authorization header
//            // response = restTemplate.postForEntity(url, requestBody, String.class)
//
//            // For now, throw TODO
//            throw new RuntimeException("TODO: Implement Nomba VA provisioning");
//
//            // Should parse response and return:
//            // {
//            //   "id": "nomba_va_xyz",
//            //   "account_number": "0123456789",  // NUBAN
//            //   "bank_name": "Nomba",
//            //   "account_name": customerName,
//            //   "status": "ACTIVE"
//            // }
//
//        } catch (RestClientException e) {
//            log.error("Failed to create virtual account on Nomba", e);
//            throw new RuntimeException("Nomba API error: " + e.getMessage(), e);
//        } catch (Exception e) {
//            log.error("Unexpected error creating virtual account", e);
//            throw new RuntimeException("Failed to provision virtual account", e);
//        }
//    }
//
//    /**
//     * Query transactions from Nomba for a merchant's account.
//     * Used for reconciliation (matching Nomba records with Cyrus records).
//     *
//     * @param credential Merchant's Nomba credentials
//     * @param merchantId Nomba merchant ID
//     * @param startDate Start date for transaction query
//     * @param endDate End date for transaction query
//     * @return List of transactions from Nomba
//     */
//    public List<NombaTransactionResponse> getTransactions(
//            NombaCredential credential,
//            String merchantId,
//            LocalDate startDate,
//            LocalDate endDate) {
//
//        try {
//            log.info("Querying Nomba transactions for merchant: {} ({}..{})",
//                merchantId, startDate, endDate);
//
//            // Step 1: Get access token
//            // TODO: decrypt credential.encryptedClientSecret() before use
//            String accessToken = authenticate(credential.clientId(), credential.encryptedClientSecret());
//
//            // Step 2: Build query parameters
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//            String startDateStr = startDate.format(formatter);
//            String endDateStr = endDate.format(formatter);
//
//            String url = nombaBaseUrl + "/transactions?" +
//                "merchant_id=" + merchantId +
//                "&start_date=" + startDateStr +
//                "&end_date=" + endDateStr;
//
//            log.debug("Querying Nomba: {}", url);
//
//            // TODO: Make HTTP GET request with Authorization header
//            // response = restTemplate.getForEntity(url, String.class)
//
//            throw new RuntimeException("TODO: Implement Nomba transaction query");
//
//            // Should return list of:
//            // {
//            //   "id": "nomba_evt_xyz",
//            //   "type": "transfer.received",
//            //   "account_number": "0123456789",
//            //   "amount": 50000,
//            //   "sender_name": "ACME CORP",
//            //   "sender_account": "0987654321",
//            //   "received_at": "2026-07-02T10:30:00Z",
//            //   "reference": "June Salary"
//            // }
//
//        } catch (RestClientException e) {
//            log.error("Failed to query Nomba transactions", e);
//            throw new RuntimeException("Nomba API error: " + e.getMessage(), e);
//        } catch (Exception e) {
//            log.error("Unexpected error querying transactions", e);
//            throw new RuntimeException("Failed to query transactions", e);
//        }
//    }
//
//    /**
//     * Verify webhook signature from Nomba.
//     * All Nomba webhooks are signed with HMAC-SHA256 using a shared secret.
//     *
//     * @param payload Raw webhook payload (JSON string)
//     * @param signature Signature from X-Nomba-Signature header
//     * @param webhookSecret Merchant's webhook secret from Nomba
//     * @return true if signature is valid, false otherwise
//     */
//    public boolean verifyWebhookSignature(String payload, String signature, String webhookSecret) {
//        try {
//            log.debug("Verifying Nomba webhook signature");
//
//            // HMAC-SHA256(payload, webhookSecret)
//            Mac mac = Mac.getInstance("HmacSHA256");
//            SecretKeySpec secretKey = new SecretKeySpec(
//                webhookSecret.getBytes(StandardCharsets.UTF_8),
//                0,
//                webhookSecret.getBytes(StandardCharsets.UTF_8).length,
//                "HmacSHA256"
//            );
//            mac.init(secretKey);
//
//            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
//            byte[] hmacBytes = mac.doFinal(payloadBytes);
//            String computedSignature = "sha256=" + HexFormat.of().formatHex(hmacBytes);
//
//            boolean valid = computedSignature.equals(signature);
//            log.debug("Webhook signature verification: {}", valid ? "VALID" : "INVALID");
//
//            return valid;
//
//        } catch (Exception e) {
//            log.error("Error verifying webhook signature", e);
//            return false;
//        }
//    }
//
//    /**
//     * Check account balance or status with Nomba.
//     * Can be used for health checks or account validation.
//     */
//    public NombaAccountStatusResponse checkAccountStatus(
//            NombaCredential credential,
//            String nombaAccountId) {
//
//        try {
//            log.info("Checking Nomba account status: {}", nombaAccountId);
//
//            // TODO: Call Nomba API to check account status
//            throw new RuntimeException("TODO: Implement Nomba account status check");
//
//        } catch (Exception e) {
//            log.error("Failed to check Nomba account status", e);
//            throw new RuntimeException("Failed to check account status", e);
//        }
//    }
//
//    /**
//     * Handle rate limiting and retries.
//     * Nomba APIs have rate limits; implement exponential backoff for retries.
//     */
//    private void handleRateLimit(int retryAfterSeconds) {
//        try {
//            log.warn("Rate limited by Nomba API. Waiting {} seconds before retry", retryAfterSeconds);
//            Thread.sleep(retryAfterSeconds * 1000L);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("Rate limit wait interrupted", e);
//        }
//    }
//}
//
//// ============ DTOs ============
//
//record NombaVirtualAccountResponse(
//    String id,
//    String accountNumber,
//    String bankName,
//    String accountName,
//    String status,
//    String createdAt
//) {}
//
//record NombaTransactionResponse(
//    String id,
//    String type,
//    String accountNumber,
//    Long amount,
//    String currency,
//    String senderName,
//    String senderAccount,
//    String senderBank,
//    String reference,
//    String receivedAt
//) {}
//
//record NombaAccountStatusResponse(
//    String id,
//    String status,
//    Long balance,
//    String accountNumber,
//    String accountName
//) {}
