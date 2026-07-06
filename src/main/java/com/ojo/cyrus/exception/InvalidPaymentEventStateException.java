package com.ojo.cyrus.exception;

/** Thrown when a payment event operation (replay, reattribute) is invalid for the event's current
 * state — e.g. reattributing an event that isn't IGNORED, or one whose provider isn't supported. */
public class InvalidPaymentEventStateException extends RuntimeException {
    public InvalidPaymentEventStateException(String message) {
        super(message);
    }
}
