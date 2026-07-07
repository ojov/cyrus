package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.MerchantCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantCustomerRepository extends JpaRepository<MerchantCustomer, UUID> {

    boolean existsByMerchantIdAndExternalCustomerId(UUID merchantId, String externalCustomerId);

    Optional<MerchantCustomer> findByMerchantIdAndExternalCustomerId(UUID merchantId, String externalCustomerId);

    long countByMerchantId(UUID merchantId);
}
