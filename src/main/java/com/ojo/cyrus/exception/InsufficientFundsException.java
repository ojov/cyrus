package com.ojo.cyrus.exception;

/** Raised when a wallet debit (e.g. a payout) would push the merchant's balance below zero. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
