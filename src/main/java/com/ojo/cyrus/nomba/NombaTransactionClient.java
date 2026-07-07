package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Reconciliation entry point: asks Nomba directly what it knows about a transfer session. Nomba is
 * treated as the source of truth — reconciliation requeries this to confirm/settle a transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NombaTransactionClient {

    private final NombaRestClients restClients;

    public NombaTransactionData requeryTransaction(Environment env, String sessionId) {
        NombaApiResponse<NombaTransactionData> response = restClients.forEnvironment(env).get()
                .uri(NombaApiUri.TRANSACTION_REQUERY.path(), sessionId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return NombaResponseSupport.requireData(response, "transaction requery " + sessionId);
    }
}
