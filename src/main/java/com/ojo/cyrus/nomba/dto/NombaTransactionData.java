package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;

/**
 * Response shape for {@code GET /v1/transactions/requery/{sessionId}}, confirmed against a real
 * live response (2026-07-07). Note the field names differ from the webhook payload's
 * {@code transaction} object: the requery returns {@code id} (not {@code transactionId}) and
 * {@code amount} (not {@code transactionAmount}). Amounts are naira decimal strings (e.g.
 * {@code "150.0"}) — convert to kobo via {@link NombaCurrencyUtil#nairaToKobo(String)}.
 *
 * <p><strong>There is no fee field in this response</strong> — a prior assumption that it carried
 * {@code fixedCharge} was wrong (verified live: the field is simply absent; the response's only
 * fee-adjacent field, {@code customerCommission}, was {@code "0.00"} and represents something else
 * entirely — not the ₦10 actually deducted). The webhook payload's {@code transaction.fee} is the
 * only place Nomba reports the fee at all, so {@link com.ojo.cyrus.services.ReconciliationService}
 * treats the fee captured at ingestion ({@code Transaction.fee}) as authoritative once this requery
 * confirms the transaction, rather than trying to re-derive it from here.
 *
 * <p>For a session Nomba doesn't recognize it returns a success envelope with a zeroed/blank
 * transaction rather than an error, so {@code transactionId} (mapped from {@code id}) being
 * non-blank is the only reliable "found" signal — see
 * {@link com.ojo.cyrus.services.ReconciliationService}, which relies on that rather than
 * {@code status}.
 */
public record NombaTransactionData(
        @JsonProperty("id") String transactionId,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("amount") String transactionAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") String status
) {}
