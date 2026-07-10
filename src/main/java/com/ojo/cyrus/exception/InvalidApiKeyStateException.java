package com.ojo.cyrus.exception;

/** Thrown for an operation that's invalid given an API key's current state — e.g. attempting
 * to delete a key that is not yet revoked. */
public class InvalidApiKeyStateException extends RuntimeException {
    public InvalidApiKeyStateException(String message) {
        super(message);
    }
}
