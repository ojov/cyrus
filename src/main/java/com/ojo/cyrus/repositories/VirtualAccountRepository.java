package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findByMerchantCustomerId(UUID merchantCustomerId);

    // Attribution entry point: an inbound transfer names the credited account number.
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    long countByMerchantCustomerMerchantId(UUID merchantId);
}
