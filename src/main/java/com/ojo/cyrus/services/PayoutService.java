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
import com.ojo.cyrus.models.entities.Payout;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.CreatePayoutRequest;
import com.ojo.cyrus.models.responses.PayoutResponse;
import com.ojo.cyrus.nomba.NombaTransferClient;
import com.ojo.cyrus.nomba.dto.NombaBankTransferData;
import com.ojo.cyrus.nomba.dto.NombaBankTransferRequest;
import com.ojo.cyrus.repositories.BeneficiaryRepository;
import com.ojo.cyrus.repositories.PayoutRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
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
            return finalizeAccepted(reserved.payoutId(), reserved.transactionId(), result);
        } catch (RuntimeException e) {
            log.warn("Payout {} failed at provider — refunding wallet: {}", reserved.reference(), e.getMessage());
            return finalizeFailed(reserved.payoutId(), reserved.transactionId(), e.getMessage());
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

    private Reserved reserve(UUID merchantId, CreatePayoutRequest request) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Merchant merchant = merchantService.findById(merchantId);
            Beneficiary beneficiary = beneficiaryRepository.findByIdAndMerchantId(request.beneficiaryId(), merchantId)
                    .orElseThrow(() -> new EntityNotFoundException("Beneficiary not found"));

            String reference = Mapper.generateReference("pyt");
            Transaction tx = transactionRepository.save(Transaction.builder()
                    .merchant(merchant)
                    .type(TransactionType.PAYOUT)
                    .reference(reference)
                    .amount(request.amount())
                    .currency(CurrencyCode.NGN)
                    .narration(request.narration())
                    .status(TransactionStatus.PENDING)
                    .matchStatus(MatchStatus.UNMATCHED)
                    .receivedAt(Instant.now())
                    .build());

            // Debit the wallet up front (throws InsufficientFundsException if the balance is too low).
            ledgerService.debit(merchant, request.amount(), tx, LedgerEntryType.PAYOUT, "Payout " + reference);

            Payout payout = payoutRepository.save(Payout.builder()
                    .merchant(merchant)
                    .beneficiary(beneficiary)
                    .transaction(tx)
                    .reference(reference)
                    .amount(request.amount())
                    .narration(request.narration())
                    .status(PayoutStatus.PENDING)
                    .build());

            return new Reserved(payout.getId(), tx.getId(), reference, merchant.getBusinessName(),
                    beneficiary.getAccountNumber(), beneficiary.getAccountName(), beneficiary.getBankCode());
        });
    }

    private PayoutResponse finalizeAccepted(UUID payoutId, UUID transactionId, NombaBankTransferData result) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Payout payout = payoutRepository.findById(payoutId).orElseThrow();
            boolean succeeded = result != null && "SUCCESS".equalsIgnoreCase(result.status());
            payout.setStatus(succeeded ? PayoutStatus.SUCCESS : PayoutStatus.PROCESSING);
            payout.setProviderReference(result != null ? result.id() : null);

            transactionRepository.findById(transactionId).ifPresent(tx ->
                    tx.setStatus(succeeded ? TransactionStatus.SUCCESSFUL : TransactionStatus.PENDING));

            log.info("Payout {} accepted by Nomba: status={}", payout.getReference(), payout.getStatus());
            return toResponse(payout);
        });
    }

    private PayoutResponse finalizeFailed(UUID payoutId, UUID transactionId, String reason) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Payout payout = payoutRepository.findById(payoutId).orElseThrow();
            Transaction tx = transactionRepository.findById(transactionId).orElseThrow();

            // Refund the reserved amount — the provider rejected the transfer, so the money never left.
            ledgerService.credit(payout.getMerchant(), payout.getAmount(), tx,
                    LedgerEntryType.REVERSAL, "Refund of failed payout " + payout.getReference());

            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(reason);
            tx.setStatus(TransactionStatus.FAILED);
            return toResponse(payout);
        });
    }

    /** kobo (integer) → naira decimal string, Nomba's provider-boundary representation. */
    private static String koboToNaira(BigInteger kobo) {
        return new BigDecimal(kobo).movePointLeft(2).toPlainString();
    }

    static PayoutResponse toResponse(Payout p) {
        return new PayoutResponse(p.getId(), p.getReference(), p.getStatus(), p.getAmount(), p.getFee(),
                p.getBeneficiary().getId(), p.getProviderReference(), p.getFailureReason(), p.getCreatedAt());
    }
}
