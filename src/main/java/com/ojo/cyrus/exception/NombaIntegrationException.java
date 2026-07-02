package com.ojo.cyrus.exception;

public class NombaIntegrationException extends RuntimeException {
    public NombaIntegrationException(String message) {
        super(message);
    }

    public NombaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
