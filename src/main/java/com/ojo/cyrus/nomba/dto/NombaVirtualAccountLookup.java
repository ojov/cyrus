package com.ojo.cyrus.nomba.dto;

import java.time.Instant;

/**
 * Result of {@link com.ojo.cyrus.nomba.clients.NombaVirtualAccountClient#getVirtualAccountCached}
 * — the live Nomba detail plus whether it came from the short-lived in-memory cache or a fresh call,
 * so a caller (e.g. the customer-detail verification) can surface how current the check actually is.
 */
public record NombaVirtualAccountLookup(NombaVirtualAccountDetail detail, boolean fromCache, Instant fetchedAt) {
}
