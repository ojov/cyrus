package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.Environment;

import java.math.BigInteger;

/** A merchant wallet's available balance for one environment, in integer kobo. */
public record WalletBalanceResponse(
        Environment environment,
        BigInteger availableBalance
) {}
