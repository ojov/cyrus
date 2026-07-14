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
    // Sub-account variant of the payout transfer — Cyrus transacts under a sub-account, so a payout
    // must target it the same way VA creation does (see VIRTUAL_ACCOUNT_UNDER_SUBACCOUNT). Confirmed
    // against https://developer.nomba.com/docs/products/transfers/transfer-to-banks.
    BANK_TRANSFER_UNDER_SUBACCOUNT("/v2/transfers/bank/{subAccountId}"),
    BANK_LOOKUP("/v1/transfers/bank/lookup"),
    // Plural "banks" — confirmed against https://developer.nomba.com/docs/products/transfers/fetch-bank-codes-and-names.
    // Was wrongly singular ("/v1/transfers/bank", colliding with the payout path's base) — BeneficiaryService.listBanks()
    // wraps every call in a try/catch that fails soft to an empty list, so this 404'd silently: the bank picker on
    // the beneficiaries page never populated, with no visible error anywhere.
    BANK_LIST("/v1/transfers/banks"),
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
    SUSPEND_VIRTUAL_ACCOUNT("/v1/accounts/suspend/{accountId}"),
    /**
     * Requery a single transfer (payout) by its provider transaction reference, scoped to a
     * sub-account: {@code GET /v1/transactions/accounts/{subAccountId}/single?transactionRef=...}.
     * Only the sub-account variant has been verified against Nomba's API docs; there is no known
     * non-sub-account equivalent endpoint — if none is configured, the client throws.
     */
    TRANSFER_REQUERY_UNDER_SUBACCOUNT("/v1/transactions/accounts/{subAccountId}/single"),
    /**
     * Lists/filters all transactions on a sub-account — {@code POST /v1/transactions/accounts/{subAccountId}}
     * with {@code dateFrom}/{@code dateTo}/{@code limit}/{@code cursor} as query params and optional
     * filters (type/status/source/...) in the body. Confirmed to exist against
     * <a href="https://developer.nomba.com/nomba-api-reference/transactions/filter-sub-account-transactions">...</a> —
     * used by {@link com.ojo.cyrus.services.MissingWebhookSweepService} to catch a payment whose
     * webhook was never delivered at all. The response item schema is verified against real responses
     * (a VA credit carries {@code id}/{@code sessionId}/{@code amount}/{@code fixedCharge}/
     * {@code entryType}/{@code recipientAccountNumber}/{@code virtualAccountReference}/{@code status},
     * and the {@code id} equals the webhook's {@code transactionId}); items are still walked as raw
     * {@code JsonNode}s so any additional/rare field Nomba sends isn't silently dropped.
     */
    SUBACCOUNT_TRANSACTIONS_FILTER("/v1/transactions/accounts/{subAccountId}");

    private final String path;

    NombaApiUri(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
