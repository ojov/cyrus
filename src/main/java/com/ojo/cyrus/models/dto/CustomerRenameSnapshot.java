package com.ojo.cyrus.models.dto;

/**
 * Customer/VA fields materialized in {@code CustomerService.rename}'s read-only phase — carried
 * across the (no-transaction) Nomba rename call into the write phase, per the provider-call
 * convention of never holding a DB transaction open across an external HTTP call.
 */
public record CustomerRenameSnapshot(String reference, String firstName, String lastName) {}
