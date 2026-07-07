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
    SUBACCOUNT_BALANCE("/v1/accounts/{subAccountId}/balance");

    private final String path;

    NombaApiUri(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
