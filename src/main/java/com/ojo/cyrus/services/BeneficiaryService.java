package com.ojo.cyrus.services;

import com.ojo.cyrus.exception.AccountVerificationException;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Beneficiary;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.CreateBeneficiaryRequest;
import com.ojo.cyrus.models.responses.AccountVerificationResponse;
import com.ojo.cyrus.models.responses.BankResponse;
import com.ojo.cyrus.models.responses.BeneficiaryResponse;
import com.ojo.cyrus.nomba.clients.NombaTransferClient;
import com.ojo.cyrus.nomba.dto.NombaBankLookupData;
import com.ojo.cyrus.nomba.dto.NombaBankLookupRequest;
import com.ojo.cyrus.repositories.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages a merchant's saved bank beneficiaries (a Cyrus-side convenience — Nomba has no
 * saved-beneficiary API; payouts settle directly to accountNumber + bankCode). On creation the
 * account is verified against Nomba's bank-lookup so the stored account name is the real one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final MerchantService merchantService;
    private final NombaTransferClient nombaTransferClient;

    /**
     * Verifies a destination account against the provider and returns the resolved holder name, for
     * the merchant to confirm before adding. Throws {@link AccountVerificationException} (422) if the
     * account can't be resolved for the chosen bank.
     */
    public AccountVerificationResponse verifyAccount(String accountNumber, String bankCode) {
        return new AccountVerificationResponse(accountNumber, resolveVerifiedName(accountNumber, bankCode), bankCode);
    }

    /**
     * Not wrapped in a method-level transaction: the Nomba lookup runs with no DB connection held
     * ({@code merchantService.findById} and the final {@code save} each open their own short tx).
     * Verification is an authoritative gate — a beneficiary is never stored unless the provider
     * confirms the account, so payouts can trust the saved details without re-validating.
     */
    public BeneficiaryResponse create(UUID merchantId, CreateBeneficiaryRequest request) {
        Merchant merchant = merchantService.findById(merchantId);
        if (beneficiaryRepository.existsByMerchantIdAndAccountNumberAndBankCode(
                merchantId, request.accountNumber(), request.bankCode())) {
            throw new AlreadyExistsException("This beneficiary is already registered");
        }

        String accountName = resolveVerifiedName(request.accountNumber(), request.bankCode());

        Beneficiary saved = beneficiaryRepository.save(Beneficiary.builder()
                .merchant(merchant)
                // No separate nickname — the label is the verified account name the merchant saw and confirmed.
                .nickname(accountName)
                .accountName(accountName)
                .accountNumber(request.accountNumber())
                .bankCode(request.bankCode())
                .bankName(request.bankName())
                .build());
        log.info("Registered beneficiary {} for merchant {}", saved.getId(), merchantId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(UUID merchantId) {
        return beneficiaryRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream().map(BeneficiaryService::toResponse).toList();
    }

    /**
     * The payable banks and their NIP codes, for populating a bank picker when registering a
     * beneficiary — the code must come from this list, not be hand-typed, so the (bankCode, bankName)
     * pair saved on a {@link Beneficiary} is always internally consistent and something Nomba
     * actually recognizes at transfer time.
     */
    public List<BankResponse> listBanks() {
        try {
            return nombaTransferClient.listBanks().stream()
                    .map(b -> new BankResponse(b.code(), b.name()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Failed to fetch bank list from Nomba, returning empty: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public Beneficiary requireOwned(UUID merchantId, UUID beneficiaryId) {
        return beneficiaryRepository.findByIdAndMerchantId(beneficiaryId, merchantId)
                .orElseThrow(() -> new EntityNotFoundException("Beneficiary not found"));
    }

    /**
     * Provider-verifies the account and returns the resolved holder name, or throws
     * {@link AccountVerificationException} (422) if it can't be confirmed. This is a hard gate — no
     * soft fallback — so a stored beneficiary always carries a real, provider-confirmed account name.
     */
    private String resolveVerifiedName(String accountNumber, String bankCode) {
        NombaBankLookupData lookup;
        try {
            lookup = nombaTransferClient.lookupAccount(new NombaBankLookupRequest(accountNumber, bankCode));
        } catch (RuntimeException e) {
            // Chain the provider failure as the cause — GlobalExceptionHandler logs the throwable
            // (with its cause), so no separate log-before-throw is needed here.
            throw new AccountVerificationException(
                    "Couldn't verify this account. Check the account number and selected bank.", e);
        }
        if (lookup == null || lookup.accountName() == null || lookup.accountName().isBlank()) {
            throw new AccountVerificationException(
                    "Couldn't verify this account. Check the account number and selected bank.");
        }
        return lookup.accountName();
    }

    static BeneficiaryResponse toResponse(Beneficiary b) {
        return new BeneficiaryResponse(b.getId(), b.getNickname(), b.getAccountName(), b.getAccountNumber(),
                b.getBankCode(), b.getBankName(), b.getCreatedAt());
    }
}
