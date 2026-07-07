package com.ojo.cyrus.nomba;

import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.nomba.dto.NombaApiResponse;

/** Shared unwrapping for the standard Nomba envelope (success == code "00"). */
final class NombaResponseSupport {

    private NombaResponseSupport() {}

    /**
     * Returns the {@code data} payload of a successful Nomba response, or throws
     * {@link NombaIntegrationException} describing the failure. Note Nomba signals errors in the
     * {@code code} field even on HTTP 200, so this checks {@code isSuccess()}, not the status code.
     */
    static <T> T requireData(NombaApiResponse<T> response, String action) {
        if (response == null || !response.isSuccess() || response.data() == null) {
            String detail = response != null ? response.description() : "null response from Nomba";
            throw new NombaIntegrationException("Nomba " + action + " failed: " + detail);
        }
        return response.data();
    }
}
