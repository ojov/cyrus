package com.ojo.cyrus.models.responses;

import org.springframework.data.domain.Page;

/**
 * A customer's identity, reporting summary, and paginated (optionally filtered) transaction
 * history. Money stays in kobo — convert to naira only at the display edge.
 */
public record CustomerStatementResponse(
        CustomerResponse customer,
        StatementSummaryResponse summary,
        Page<StatementRowResponse> transactions
) {}
