package com.ojo.cyrus.services;

import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Beneficiary;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.CreateBeneficiaryRequest;
import com.ojo.cyrus.models.responses.BankResponse;
import com.ojo.cyrus.models.responses.BeneficiaryResponse;
import com.ojo.cyrus.nomba.NombaTransferClient;
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
     * Not wrapped in a method-level transaction: the Nomba lookup runs with no DB connection held
     * ({@code merchantService.findById} and the final {@code save} each open their own short tx).
     */
    public BeneficiaryResponse create(UUID merchantId, CreateBeneficiaryRequest request) {
        Merchant merchant = merchantService.findById(merchantId);
        if (beneficiaryRepository.existsByMerchantIdAndAccountNumberAndBankCode(
                merchantId, request.accountNumber(), request.bankCode())) {
            throw new AlreadyExistsException("This beneficiary is already registered");
        }

        String accountName = resolveAccountName(request);

        Beneficiary saved = beneficiaryRepository.save(Beneficiary.builder()
                .merchant(merchant)
                .nickname(request.nickname())
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
        return nombaTransferClient.listBanks().stream()
                .map(b -> new BankResponse(b.code(), b.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Beneficiary requireOwned(UUID merchantId, UUID beneficiaryId) {
        return beneficiaryRepository.findByIdAndMerchantId(beneficiaryId, merchantId)
                .orElseThrow(() -> new EntityNotFoundException("Beneficiary not found"));
    }

    /** Verifies the destination account name via Nomba; falls back to the nickname on lookup failure. */
    private String resolveAccountName(CreateBeneficiaryRequest request) {
        try {
            NombaBankLookupData lookup = nombaTransferClient.lookupAccount(
                    new NombaBankLookupRequest(request.accountNumber(), request.bankCode()));
            if (lookup != null && lookup.accountName() != null && !lookup.accountName().isBlank()) {
                return lookup.accountName();
            }
        } catch (RuntimeException e) {
            log.warn("Bank lookup failed for {}/{} — storing beneficiary without verified name: {}",
                    request.accountNumber(), request.bankCode(), e.getMessage());
        }
        return request.nickname();
    }

    static BeneficiaryResponse toResponse(Beneficiary b) {
        return new BeneficiaryResponse(b.getId(), b.getNickname(), b.getAccountName(), b.getAccountNumber(),
                b.getBankCode(), b.getBankName(), b.getCreatedAt());
    }
}
