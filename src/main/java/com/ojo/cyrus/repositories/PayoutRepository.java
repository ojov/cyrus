package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Payout> findByReference(String reference);

    Page<Payout> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
}
