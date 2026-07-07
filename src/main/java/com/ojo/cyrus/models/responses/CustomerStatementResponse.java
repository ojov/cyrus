package com.ojo.cyrus.models.responses;

import org.springframework.data.domain.Page;

import java.math.BigInteger;

/**
 * A customer's identity summary plus their paginated transaction history. Money stays in kobo —
 * convert to naira only at the display edge.
 */
public record CustomerStatementResponse(
        CustomerResponse customer,
        BigInteger lifetimeKobo,
        Page<StatementRowResponse> transactions
) {}
