package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.entities.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    Optional<Beneficiary> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Beneficiary> findByMerchantIdAndEnvironmentOrderByCreatedAtDesc(UUID merchantId, Environment environment);

    boolean existsByMerchantIdAndAccountNumberAndBankCodeAndEnvironment(
            UUID merchantId, String accountNumber, String bankCode, Environment environment);
}
