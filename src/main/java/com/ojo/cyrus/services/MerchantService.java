package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.SubAccountBalanceResponse;
import com.ojo.cyrus.nomba.CredentialMapper;
import com.ojo.cyrus.nomba.NombaClient;
import com.ojo.cyrus.nomba.NombaCredentials;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.repositories.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final NombaClient nombaClient;
    private final CredentialMapper credentialMapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public Merchant findById(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    /**
     * Resolves a merchant's Nomba credentials into a detached, fully-materialized value so
     * callers can perform the external Nomba call without holding a DB transaction open.
     */
    @Transactional(readOnly = true)
    public NombaCredentials getNombaCredentials(UUID merchantId) {
        return credentialMapper.fromMerchant(findById(merchantId));
    }

    public Merchant findByBusinessEmail(String email) {
        return merchantRepository.findByBusinessEmail(email).orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    public Merchant save(Merchant merchant) {
        return merchantRepository.save(merchant);
    }

    // NOT_SUPPORTED opts this method out of the class-level @Transactional so the external
    // Nomba calls do not run inside a DB transaction. Credentials (incl. parent + sub-account
    // ids) are materialized in a short read-only transaction first, then the connection is
    // released before the HTTP calls.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SubAccountBalanceResponse> getSubAccountBalances(String email, Environment env) {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);
        NombaCredentials creds = readOnlyTx.execute(status ->
                credentialMapper.fromMerchant(findByBusinessEmail(email)));

        List<SubAccountBalanceResponse> balances = new ArrayList<>();

        NombaBalanceData parentBalance = nombaClient.getParentAccountBalance(creds, env);
        balances.add(new SubAccountBalanceResponse(
                creds.parentAccountId(), "PARENT",
                parentBalance.amountInKobo(), parentBalance.currency(), Instant.now()));

        for (String subAccountId : creds.subAccountIds()) {
            NombaBalanceData data = nombaClient.getSubAccountBalance(creds, subAccountId, env);
            balances.add(new SubAccountBalanceResponse(
                    subAccountId, "SUB",
                    data.amountInKobo(), data.currency(), Instant.now()));
        }

        return balances;
    }

    public void updateSubAccounts(String email, Set<String> subAccountIds) {
        Merchant merchant = findByBusinessEmail(email);
        merchant.setNombaSubAccountIds(subAccountIds);
    }

    public void validateMerchantExists(MerchantRegistrationRequest request) {
        if (merchantRepository.existsByBusinessEmail(request.businessEmail())) {
            throw new AlreadyExistsException("An account with this email already exists");
        }
        if (merchantRepository.existsByNombaParentAccountId(request.nombaParentAccountId())) {
            throw new AlreadyExistsException("Nomba parent account already registered");
        }
    }

}
