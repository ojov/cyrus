package com.ojo.cyrus.exception;

/**
 * Thrown when a destination bank account can't be verified against the provider (bad account number
 * for the chosen bank, or the provider couldn't resolve a name). A client error — the merchant's
 * input is unprocessable — handled as a 422, never a 500.
 */
public class AccountVerificationException extends RuntimeException {
    public AccountVerificationException(String message) {
        super(message);
    }

    public AccountVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
