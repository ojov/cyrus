package com.ojo.cyrus.exception;

/**
 * Thrown when a merchant tries to perform an action that requires a verified email
 * (like going live) before they have verified it.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
