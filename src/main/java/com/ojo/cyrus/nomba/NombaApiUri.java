package com.ojo.cyrus.nomba;

/**
 * Central catalog of Nomba API paths (relative — the base URL is baked into the per-environment
 * {@code RestClient}). Paths with {@code {placeholders}} are expanded via RestClient URI variables.
 */
public enum NombaApiUri {

    TOKEN_ISSUE("/v1/auth/token/issue"),
    VIRTUAL_ACCOUNT("/v1/accounts/virtual"),
    VIRTUAL_ACCOUNT_UNDER_SUBACCOUNT("/v1/accounts/virtual/{subAccountId}"),
    VIRTUAL_ACCOUNT_BY_REF("/v1/accounts/virtual/{accountRef}"),
    TRANSACTION_REQUERY("/v1/transactions/requery/{sessionId}"),
    BANK_TRANSFER("/v2/transfers/bank"),
    BANK_LOOKUP("/v1/transfers/bank/lookup"),
    BANK_LIST("/v1/transfers/bank"),
    PARENT_BALANCE("/v1/accounts/parent/balance"),
    SUBACCOUNT_BALANCE("/v1/accounts/{subAccountId}/balance"),
    /**
     * Suspend (and presumably unsuspend) a virtual account. Nomba's documentation specifies this as
     * {@code PUT /v1/accounts/suspend/{accountId}}; the path parameter accepts the VA's
     * {@code accountHolderId} (UUID) or possibly its {@code accountRef} / {@code bankAccountNumber}.
     *
     * <p><strong>Not verified during the hackathon:</strong> the sandbox environment (and our
     * hackathon-provisioned production account) returned 403 ("Forbidden") for every identifier
     * format we tried ({@code accountRef}, {@code accountHolderId}, {@code bankAccountNumber}),
     * suggesting Nomba disabled the suspend/unsuspend feature set on our account tier. The endpoint
     * is wired here so the lifecycle is complete — the same two-phase pattern used for
     * {@code expireVirtualAccount} — and once Nomba enables it the code should work with no changes.
     * Until then, SUSPENDED→ACTIVE (reactivate) is also untestable; it may be the same endpoint
     * acting as a toggle, or a separate endpoint we haven't discovered.
     */
    SUSPEND_VIRTUAL_ACCOUNT("/v1/accounts/suspend/{accountId}");

    private final String path;

    NombaApiUri(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
