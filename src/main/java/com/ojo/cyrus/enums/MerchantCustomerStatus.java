package com.ojo.cyrus.enums;

/**
 * Lifecycle of a {@link com.ojo.cyrus.models.entities.MerchantCustomer}. ACTIVE⇄SUSPENDED is
 * reversible and Cyrus-local; CLOSED is terminal (soft-delete — row + VA + history stay intact) and
 * additionally expires the virtual account on Nomba's side. Cascades to the 1:1 virtual account.
 */
public enum MerchantCustomerStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
