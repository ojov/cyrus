package com.ojo.cyrus.models.responses;

import java.math.BigDecimal;

/** A merchant wallet's available balance, in kobo at scale 4. */
public record WalletBalanceResponse(
        BigDecimal availableBalance
) {}
