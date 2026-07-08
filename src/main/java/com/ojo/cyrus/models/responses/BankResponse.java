package com.ojo.cyrus.models.responses;

/** One payable bank and its NIP code, for populating a bank picker when registering a beneficiary. */
public record BankResponse(String code, String name) {}
