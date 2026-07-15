package com.ojo.cyrus.models.responses;

import java.time.Instant;
import java.util.List;

/**
 * Diffs Cyrus's local {@code VirtualAccount} table against Nomba's live virtual-account list.
 * {@code leakedOnNomba} is the case this audit exists to catch: a VA Nomba knows about with no
 * matching local row (the known non-atomic-create failure mode — the Nomba call succeeds but the
 * subsequent local write then fails). {@code missingOnNomba} and {@code statusDrift} catch the rarer
 * inverse cases. Each bucket is capped at 100 items; see the {@code *Truncated} flags.
 */
public record VirtualAccountAuditResponse(
        int totalLocalVirtualAccounts,
        int totalNombaVirtualAccounts,
        List<VirtualAccountAuditItem> leakedOnNomba,
        boolean leakedOnNombaTruncated,
        List<VirtualAccountAuditItem> missingOnNomba,
        boolean missingOnNombaTruncated,
        List<VirtualAccountAuditItem> statusDrift,
        boolean statusDriftTruncated,
        boolean nombaListTruncated,
        Instant auditedAt
) {
    public record VirtualAccountAuditItem(
            String accountRef,
            String bankAccountNumber,
            String merchantBusinessName,
            String localStatus,
            String nombaStatus
    ) {}
}
