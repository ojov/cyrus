package com.ojo.cyrus.exception;

/** Thrown when an authenticated caller lacks the role/permission for an endpoint (→ 403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
