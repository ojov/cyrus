package com.ojo.cyrus.services;

import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.repositories.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public Merchant findByBusinessEmail(String email) {
        return merchantRepository.findByBusinessEmail(email).orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    public Merchant save(Merchant merchant) {
        return merchantRepository.save(merchant);
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
