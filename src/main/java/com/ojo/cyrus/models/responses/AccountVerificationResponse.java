package com.ojo.cyrus.models.responses;

/**
 * The resolved account holder for a (accountNumber, bankCode) pair, shown to the merchant to confirm
 * before saving a beneficiary. {@code accountName} is the provider-verified name.
 */
public record AccountVerificationResponse(
        String accountNumber,
        String accountName,
        String bankCode
) {}
