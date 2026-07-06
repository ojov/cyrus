package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.models.entities.PaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    boolean existsByRequestId(String requestId);
    Optional<PaymentEvent> findByRequestId(String requestId);

    // Merchant-scoped listing with optional status/provider filters — a merchant only ever sees
    // their own events (nullable-param style, matching the reconciliation/webhook-delivery queries).
    @Query("""
            SELECT e FROM PaymentEvent e
            WHERE e.merchant.id = :merchantId
              AND (:status IS NULL OR e.status = :status)
              AND (:provider IS NULL OR e.provider = :provider)
            ORDER BY e.createdAt DESC
            """)
    Page<PaymentEvent> findByMerchant(@Param("merchantId") UUID merchantId,
                                       @Param("status") EventStatus status,
                                       @Param("provider") Provider provider,
                                       Pageable pageable);
}
