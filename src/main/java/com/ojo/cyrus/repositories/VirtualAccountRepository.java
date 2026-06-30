package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    boolean existsByMerchantIdAndCustomerReference(UUID merchantId, String customerReference);

    Optional<VirtualAccount> findByMerchantIdAndCustomerReference(UUID merchantId, String customerReference);
}