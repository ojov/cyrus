package com.ojo.cyrus.exception;

/** Thrown when a merchant-submitted webhook URL is rejected — malformed, or resolves to a
 * non-public address (SSRF guard). */
public class InvalidWebhookUrlException extends RuntimeException {
    public InvalidWebhookUrlException(String message) {
        super(message);
    }
}
