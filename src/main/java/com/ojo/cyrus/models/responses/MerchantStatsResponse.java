package com.ojo.cyrus.models.responses;

/** Operational counts for the admin dashboard. */
public record MerchantStatsResponse(
        long customers,
        long virtualAccounts
) {}
