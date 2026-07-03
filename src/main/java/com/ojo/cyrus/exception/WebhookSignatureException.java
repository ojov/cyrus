package com.ojo.cyrus.exception;

/**
 * Thrown when an inbound provider webhook fails HMAC signature verification. Handled as a 401 —
 * the request is rejected and never processed.
 */
public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException(String message) {
        super(message);
    }
}
