package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Reads the balance of Cyrus's own Nomba account (parent, or a sub-account). This is the pooled
 * provider-side balance across all merchants — per-merchant balances are tracked internally by the
 * {@code Wallet}/{@code LedgerEntry} ledger, not here. Useful for ops/reconciliation sanity checks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaBalanceClient {

    private final NombaRestClients restClients;

    public NombaBalanceData getParentAccountBalance(Environment env) {
        NombaApiResponse<NombaBalanceData> response = restClients.forEnvironment(env).get()
                .uri(NombaApiUri.PARENT_BALANCE.path())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "parent account balance");
    }

    public NombaBalanceData getSubAccountBalance(Environment env, String subAccountId) {
        NombaApiResponse<NombaBalanceData> response = restClients.forEnvironment(env).get()
                .uri(NombaApiUri.SUBACCOUNT_BALANCE.path(), subAccountId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "sub-account balance " + subAccountId);
    }
}
