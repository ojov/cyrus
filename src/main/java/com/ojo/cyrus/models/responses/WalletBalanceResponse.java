package com.ojo.cyrus.models.responses;

import java.math.BigInteger;

/** A merchant wallet's available balance, in integer kobo. */
public record WalletBalanceResponse(
        BigInteger availableBalance
) {}
