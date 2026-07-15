package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findByMerchantCustomerId(UUID merchantCustomerId);

    // Attribution entry point: an inbound transfer names the credited account number.
    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    long countByMerchantCustomerMerchantId(UUID merchantId);

    // Used by the super-admin VA audit (PlatformAdminService.auditVirtualAccounts) — join-fetches the
    // owning customer and merchant so accountRef/businessName are safe to read after the read-only tx
    // closes (open-in-view is false), avoiding a LazyInitializationException.
    @Query("SELECT va FROM VirtualAccount va JOIN FETCH va.merchantCustomer mc JOIN FETCH mc.merchant")
    List<VirtualAccount> findAllWithCustomerAndMerchant();
}
