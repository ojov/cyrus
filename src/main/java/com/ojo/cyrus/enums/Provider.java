package com.ojo.cyrus.enums;

/**
 * Payment provider. Only Nomba is wired today; the enum exists so provider-facing logic and stored
 * records carry an explicit provider rather than assuming Nomba.
 */
public enum Provider {
    NOMBA,
    PAYSTACK,
    MONIEPOINT
}
