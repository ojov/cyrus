package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.NombaIntegrationException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Holds one pre-configured {@link RestClient} per {@link Environment} (base URL + auth interceptor +
 * error handling baked in — see {@code NombaConfig}). Thin Nomba clients select the right one by
 * environment; they never build requests with credentials themselves.
 */
public record NombaRestClients(Map<Environment, RestClient> byEnvironment) {

    public RestClient forEnvironment(Environment env) {
        RestClient client = byEnvironment.get(env);
        if (client == null) {
            throw new NombaIntegrationException("No Nomba REST client configured for environment " + env);
        }
        return client;
    }
}
