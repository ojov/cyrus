package com.ojo.cyrus.exception;

/** Thrown for an operation that's invalid given a customer's current state — e.g. any status
 * change attempted on an already-CLOSED customer (terminal/soft-deleted). */
public class InvalidCustomerStateException extends RuntimeException {
    public InvalidCustomerStateException(String message) {
        super(message);
    }
}
