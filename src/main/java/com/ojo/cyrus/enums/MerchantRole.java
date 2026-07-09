package com.ojo.cyrus.enums;

/**
 * Authorization role for a {@link com.ojo.cyrus.models.entities.Merchant}. Almost every account is a
 * plain {@code MERCHANT}; {@code SUPER_ADMIN} is Cyrus platform staff with access to the
 * platform-wide oversight endpoints ({@code /v1/platform/**}). The first super-admin is seeded from
 * config ({@code APP_SUPER_ADMIN_EMAILS}) at startup — see {@code SuperAdminBootstrap}.
 */
public enum MerchantRole {
    MERCHANT,
    SUPER_ADMIN
}
