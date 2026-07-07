package com.ojo.cyrus.models.dto;

/**
 * Customer/VA fields materialized in {@code CustomerService.updateStatus}'s read-only phase —
 * carried across the (no-transaction) Nomba expiry call into the write phase, per the
 * provider-call convention of never holding a DB transaction open across an external HTTP call.
 */
public record CustomerStatusSnapshot(String reference) {}
