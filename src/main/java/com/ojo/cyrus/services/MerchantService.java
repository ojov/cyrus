package com.ojo.cyrus.services;

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
    private final ApiKeyService apiKeyService;


}
