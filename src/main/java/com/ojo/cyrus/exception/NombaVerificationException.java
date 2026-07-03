package com.ojo.cyrus.exception;

/**
 * Thrown when a merchant's provided (live) Nomba credentials fail verification against Nomba.
 * Handled as a 400 — the credentials are rejected and live mode is not activated.
 */
public class NombaVerificationException extends RuntimeException {
    public NombaVerificationException(String message) {
        super(message);
    }
}
