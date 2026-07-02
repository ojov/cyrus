package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByMerchantIdAndReference(UUID merchantId, String reference);

    Optional<Customer> findByMerchantIdAndReference(UUID merchantId, String reference);
}
