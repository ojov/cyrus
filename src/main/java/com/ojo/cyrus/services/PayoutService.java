package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.CurrencyCode;
import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Beneficiary;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.dto.NormalizedPayoutEvent;
import com.ojo.cyrus.models.entities.Payout;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.CreatePayoutRequest;
import com.ojo.cyrus.models.responses.PayoutResponse;
import com.ojo.cyrus.nomba.clients.NombaTransferClient;
import com.ojo.cyrus.nomba.dto.NombaBankTransferData;
import com.ojo.cyrus.nomba.dto.NombaBankTransferRequest;
import com.ojo.cyrus.repositories.BeneficiaryRepository;
import com.ojo.cyrus.repositories.PayoutRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.utils.Mapper;
import com.ojo.cyrus.utils.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Merchant payouts (wallet → bank beneficiary via Nomba). Two-phase to keep the Nomba HTTP call
 * outside any DB transaction: phase 1 reserves funds (debits the wallet with a PAYOUT ledger entry
 * and records a PENDING payout + PAYOUT transaction) in one short tx; phase 2 calls Nomba with no tx
 * open; phase 3 marks the outcome — and on a permanent failure refunds the wallet — in another short
 * tx. Debiting up front means a merchant can never over-draw by firing concurrent payouts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final LedgerService ledgerService;
    private final NombaTransferClient nombaTransferClient;
    private final PlatformTransactionManager transactionManager;
    private final FeeConfigService feeConfigService;
    private final PlatformProfitService platformProfitService;

    /** Materialized snapshot carried from the reserve phase (managed entities) across the Nomba call. */
    private record Reserved(UUID payoutId, UUID transactionId, String reference, String senderName,
                            String accountNumber, String accountName, String bankCode) {}

    public PayoutResponse initiate(UUID merchantId, CreatePayoutRequest request) {
        Reserved reserved = reserve(merchantId, request);

        try {
            NombaBankTransferData result = nombaTransferClient.transfer(new NombaBankTransferRequest(
                    koboToNaira(request.amount()),
                    reserved.accountNumber(),
                    reserved.accountName(),
                    reserved.bankCode(),
                    reserved.reference(),
                    reserved.senderName(),
                    request.narration()), reserved.reference());
            return finalizeAccepted(reserved.reference(), reserved.transactionId(), result);
        } catch (RuntimeException e) {
            log.warn("Payout {} failed at provider — refunding wallet: {}", reserved.reference(), e.getMessage());
            return finalizeFailed(reserved.reference(), reserved.transactionId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<PayoutResponse> list(UUID merchantId, Pageable pageable) {
        return payoutRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable).map(PayoutService::toResponse);
    }

    @Transactional(readOnly = true)
    public PayoutResponse getForMerchant(UUID merchantId, UUID payoutId) {
        return toResponse(payoutRepository.findByIdAndMerchantId(payoutId, merchantId)
                .orElseThrow(() -> new EntityNotFoundException("Payout not found")));
    }

    /**
     * Finalizes a payout from its authoritative Nomba webhook (payout_success / payout_failed /
     * payout_refund), matched to the {@link Payout} by {@code merchantTxRef} (== {@code reference}).
     *
     * <p>Idempotent: a duplicate or late webhook that finds the payout already terminal
     * (SUCCESS/FAILED) is a no-op. The payout row is pessimistically locked so this async path can't
     * race the synchronous initiate finalize into a double status flip or double refund. The wallet
     * was already debited up front at {@link #reserve}, so a success does NOT debit again — it only
     * confirms; a failure/refund returns the reserved funds exactly once via a {@code REVERSAL}. No
     * outbound merchant webhook is emitted — payouts are a dashboard-only feature.
     */
    public void applyWebhook(NormalizedPayoutEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Payout payout = payoutRepository.findByReferenceForUpdate(event.merchantTxRef()).orElse(null);
            if (payout == null) {
                log.warn("Payout webhook {} for unknown merchantTxRef={} — ignoring",
                        event.eventType(), event.merchantTxRef());
                return;
            }
            if (payout.getStatus() == PayoutStatus.SUCCESS || payout.getStatus() == PayoutStatus.FAILED) {
                log.info("Payout {} already {} — ignoring duplicate {} webhook",
                        payout.getReference(), payout.getStatus(), event.eventType());
                return;
            }

            Transaction tx = payout.getTransaction();
            if (event.isSuccess()) {
                payout.setStatus(PayoutStatus.SUCCESS);
                if (event.providerTransactionId() != null) {
                    payout.setProviderReference(event.providerTransactionId());
                }
                // Payout.fee is set at reserve() to Cyrus's flat fee (the total charged to the
                // merchant). We do NOT overwrite it with Nomba's fee from the webhook — the
                // Nomba fee (₦20 fixed) is deducted by Nomba from the transfer itself; it's
                // visible in the requery response's fixedCharge field if needed for audit.
                if (tx != null) {
                    tx.setStatus(TransactionStatus.SUCCESSFUL);
                }
                log.info("Payout {} confirmed SUCCESS by webhook", payout.getReference());
            } else if (event.isFailureOrRefund()) {
                // Return the reserved funds + fee (both debited up front at reserve()) exactly once.
                // The status guard above ensures this can't double-refund a payout already refunded
                // synchronously.
                if (tx != null) {
                    BigDecimal totalRefund = payout.getAmount().add(payout.getFee());
                    ledgerService.credit(payout.getMerchant(), totalRefund, tx,
                            LedgerEntryType.REVERSAL, "Refund of failed payout " + payout.getReference());
                    // Reverse the expected outflow on the profit ledger.
                    platformProfitService.reverseOutflow(payout);
                    tx.setStatus(TransactionStatus.FAILED);
                } else {
                    log.error("Payout {} has no paired transaction — cannot post refund ledger entry",
                            payout.getReference());
                }
                payout.setStatus(PayoutStatus.FAILED);
                payout.setFailureReason("payout_refund".equalsIgnoreCase(event.eventType())
                        ? "Refunded by provider" : "Failed at provider");
                log.info("Payout {} {} by webhook — wallet refunded", payout.getReference(), event.eventType());
            } else {
                log.warn("Payout webhook for {} has unexpected event_type={} — ignoring",
                        payout.getReference(), event.eventType());
            }
        });
    }

    /**
     * Periodic sweep that requeries Nomba for payouts stuck in PROCESSING with a known
     * {@code providerReference}. The synchronous initiate response may return {@code PENDING_BILLING}
     * for an accepted transfer — that sets the payout to PROCESSING and waits for the completing
     * webhook. If that webhook is lost, the sweep recovers the payout (promote SUCCESS or refund
     * FAILED). Payouts with no {@code providerReference} (no Nomba transfer ID from the sync
     * response) cannot be requeried and rely solely on the webhook.
     */
    @Scheduled(fixedDelayString = "#{${app.payout-requery.delay-seconds} * 1000}")
    public void sweepProcessingPayouts() {
        List<Payout> processing = payoutRepository.findByStatusAndProviderReferenceIsNotNull(PayoutStatus.PROCESSING);
        if (processing.isEmpty()) {
            return;
        }
        log.info("Payout requery sweep: {} payout(s) processing with provider reference", processing.size());
        for (Payout payout : processing) {
            try {
                requeryPayout(payout);
            } catch (Exception e) {
                log.error("Payout requery failed for payout {} — will retry next sweep",
                        payout.getReference(), e);
            }
        }
    }

    /**
     * Requery a single PROCESSING payout against Nomba and apply the outcome. The payout row is
     * pessimistically locked so this can't race {@link #applyWebhook}.
     */
    private void requeryPayout(Payout payout) {
        // requeryTransfer calls NombaResponseSupport.requireData which throws on null/missing data,
        // so a normal return guarantees a non-null result with a status string.
        NombaBankTransferData result = nombaTransferClient.requeryTransfer(payout.getProviderReference());
        String status = result.status();
        if (status == null) {
            log.warn("Payout {} requery returned null status — leaving for next sweep", payout.getReference());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
                Payout locked = payoutRepository.findByReferenceForUpdate(payout.getReference()).orElse(null);
                if (locked == null || locked.getStatus() != PayoutStatus.PROCESSING) {
                    return;
                }

                locked.setStatus(PayoutStatus.SUCCESS);
                // Payout.fee was set at reserve() to Cyrus's flat fee — do NOT overwrite it with
                // Nomba's fee from the requery (Nomba reports the ₦20 fixed charge as result.fee()).
                // The Nomba fee is informative only; the merchant is charged Cyrus's flat rate.
                if (result.id() != null) {
                    locked.setProviderReference(result.id());
                }

                Transaction tx = locked.getTransaction();
                if (tx != null) {
                    tx.setStatus(TransactionStatus.SUCCESSFUL);
                }
                log.info("Payout {} confirmed SUCCESS by requery sweep", locked.getReference());
            });

        } else if (!"PENDING_BILLING".equalsIgnoreCase(status)) {
            // Any status other than SUCCESS or PENDING_BILLING (e.g. REFUND, FAILED, REVERSED)
            // is a terminal failure — refund the wallet. This is intentionally broad: a missed
            // refund is worse than a false one, and PENDING_BILLING is the only non-terminal
            // non-success status Nomba returns for a transfer that's still settling.
            new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
                Payout locked = payoutRepository.findByReferenceForUpdate(payout.getReference()).orElse(null);
                if (locked == null || locked.getStatus() != PayoutStatus.PROCESSING) {
                    return;
                }

                Transaction tx = locked.getTransaction();
                if (tx != null) {
                    BigDecimal totalRefund = locked.getAmount().add(locked.getFee());
                    ledgerService.credit(locked.getMerchant(), totalRefund, tx,
                            LedgerEntryType.REVERSAL, "Refund of failed payout " + locked.getReference());
                    // Reverse the expected outflow on the profit ledger.
                    platformProfitService.reverseOutflow(locked);
                    tx.setStatus(TransactionStatus.FAILED);
                } else {
                    log.error("Payout {} has no paired transaction — cannot post refund ledger entry",
                            locked.getReference());
                }

                locked.setStatus(PayoutStatus.FAILED);
                locked.setFailureReason("Per Nomba requery");
                log.info("Payout {} {} by requery sweep — wallet refunded", locked.getReference(), status);
            });

        } else {
            log.debug("Payout {} still {} per requery — leaving for next sweep", payout.getReference(), status);
        }
    }

    private Reserved reserve(UUID merchantId, CreatePayoutRequest request) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Merchant merchant = merchantService.findById(merchantId);
            Beneficiary beneficiary = beneficiaryRepository.findByIdAndMerchantId(request.beneficiaryId(), merchantId)
                    .orElseThrow(() -> new EntityNotFoundException("Beneficiary not found"));

            String reference = Mapper.generateReference("pyt");
            BigDecimal amount = MoneyUtil.normalize(request.amount());
            Transaction tx = transactionRepository.save(Transaction.builder()
                    .merchant(merchant)
                    .type(TransactionType.PAYOUT)
                    .reference(reference)
                    .amount(amount)
                    .currency(CurrencyCode.NGN)
                    .narration(request.narration())
                    .status(TransactionStatus.PENDING)
                    .matchStatus(MatchStatus.UNMATCHED)
                    .receivedAt(Instant.now())
                    .build());

            // Debit the transfer amount + flat fee up front (throws InsufficientFundsException if the
            // balance is too low). The fee is Cyrus's fixed charge (₦30); Nomba's ₦20 is deducted
            // from the transfer itself and is never visible in the merchant's wallet.
            BigDecimal payoutFee = MoneyUtil.normalize(feeConfigService.current().getPayoutFlatFeeKobo());
            ledgerService.debit(merchant, amount.add(payoutFee), tx,
                    LedgerEntryType.PAYOUT, "Payout " + reference);

            tx.setMerchantFeeKobo(payoutFee);

            Payout payout = payoutRepository.save(Payout.builder()
                    .merchant(merchant)
                    .beneficiary(beneficiary)
                    .transaction(tx)
                    .reference(reference)
                    .amount(amount)
                    .fee(payoutFee)
                    .narration(request.narration())
                    .status(PayoutStatus.PENDING)
                    .build());

            // Mirror to platform profit ledger: expected outflow from Nomba.
            platformProfitService.recordOutflow(payout);

            return new Reserved(payout.getId(), tx.getId(), reference, merchant.getBusinessName(),
                    beneficiary.getAccountNumber(), beneficiary.getAccountName(), beneficiary.getBankCode());
        });
    }

    private PayoutResponse finalizeAccepted(String reference, UUID transactionId, NombaBankTransferData result) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            // Lock the payout row (same lock applyWebhook takes) so the synchronous outcome and the
            // async payout webhook can't both mutate it. If the webhook already finalized this payout
            // to a terminal state, never overwrite it (would revert a confirmed SUCCESS to PROCESSING).
            Payout payout = payoutRepository.findByReferenceForUpdate(reference).orElseThrow();
            if (payout.getStatus() == PayoutStatus.SUCCESS || payout.getStatus() == PayoutStatus.FAILED) {
                log.info("Payout {} already {} (finalized by webhook) — keeping synchronous response out",
                        reference, payout.getStatus());
                return toResponse(payout);
            }

            boolean succeeded = result != null && "SUCCESS".equalsIgnoreCase(result.status());
            payout.setStatus(succeeded ? PayoutStatus.SUCCESS : PayoutStatus.PROCESSING);
            payout.setProviderReference(result != null ? result.id() : null);

            transactionRepository.findById(transactionId).ifPresent(tx ->
                    tx.setStatus(succeeded ? TransactionStatus.SUCCESSFUL : TransactionStatus.PENDING));

            log.info("Payout {} accepted by Nomba: status={}", payout.getReference(), payout.getStatus());
            return toResponse(payout);
        });
    }

    private PayoutResponse finalizeFailed(String reference, UUID transactionId, String reason) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            // Lock the payout row and re-check: if the webhook already finalized it (e.g. the transfer
            // actually succeeded and our client only saw a read timeout), do NOT refund — the money
            // left. This guard is what makes the applyWebhook lock effective and prevents a double
            // refund when both this path and a payout_failed/refund webhook fire.
            Payout payout = payoutRepository.findByReferenceForUpdate(reference).orElseThrow();
            if (payout.getStatus() == PayoutStatus.SUCCESS || payout.getStatus() == PayoutStatus.FAILED) {
                log.info("Payout {} already {} (finalized by webhook) — skipping synchronous refund",
                        reference, payout.getStatus());
                return toResponse(payout);
            }

            Transaction tx = transactionRepository.findById(transactionId).orElseThrow();

            // Refund the reserved amount + fee — the provider rejected the transfer, so the money
            // never left. Payout.fee was set at reserve() to Cyrus's flat fee.
            BigDecimal totalRefund = payout.getAmount().add(payout.getFee());
            ledgerService.credit(payout.getMerchant(), totalRefund, tx,
                    LedgerEntryType.REVERSAL, "Refund of failed payout " + payout.getReference());

            // Reverse the expected outflow on the profit ledger.
            platformProfitService.reverseOutflow(payout);

            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(reason);
            tx.setStatus(TransactionStatus.FAILED);
            return toResponse(payout);
        });
    }

    /**
     * kobo → naira decimal string, Nomba's provider-boundary representation. This is the ONE place
     * money is rounded to whole kobo: Nomba's transfer API settles in whole kobo, so a fractional
     * balance can't be wired out. The {@code @Digits(fraction = 0)} guard on
     * {@code CreatePayoutRequest.amount} means the setScale here never actually rounds in practice —
     * it is a defensive belt so the ledger debit can never silently diverge from what Nomba is sent.
     */
    private static String koboToNaira(BigDecimal kobo) {
        return kobo.setScale(0, MoneyUtil.ROUNDING).movePointLeft(2).toPlainString();
    }

    static PayoutResponse toResponse(Payout p) {
        return new PayoutResponse(p.getId(), p.getReference(), p.getStatus(), p.getAmount(), p.getFee(),
                p.getBeneficiary().getId(), p.getProviderReference(), p.getFailureReason(), p.getCreatedAt());
    }
}
