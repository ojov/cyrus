package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.models.entities.NombaPaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NombaPaymentEventRepository extends JpaRepository<NombaPaymentEvent, UUID> {

    boolean existsByRequestId(String requestId);

    Optional<NombaPaymentEvent> findByRequestId(String requestId);

    // Merchant-scoped listing with optional status filter — a merchant only ever sees their own
    // events (orphans with no resolved merchant are invisible here). Single-provider now, so no
    // provider filter (nullable-param style, matching the webhook-delivery queries).
    @Query("""
            SELECT e FROM NombaPaymentEvent e
            WHERE e.merchant.id = :merchantId
              AND (:status IS NULL OR e.status = :status)
            ORDER BY e.createdAt DESC
            """)
    Page<NombaPaymentEvent> findByMerchant(@Param("merchantId") UUID merchantId,
                                           @Param("status") NombaPaymentEventStatus status,
                                           Pageable pageable);

    // Overview reconciliation-health count: events that never became a transaction at all — the
    // orphan/misdirected-payment case, distinct from a transaction that DID get created but has a
    // DISCREPANCY/MANUAL_REVIEW matchStatus.
    long countByMerchantIdAndStatus(UUID merchantId, NombaPaymentEventStatus status);

    // Platform oversight: fully-unattributable orphans (no owning merchant resolved at all) — these
    // are invisible through every merchant-scoped surface, so the super-admin view is the only place
    // they show up.
    long countByMerchantIsNull();

    // Paginated listing of fully-unattributable orphans — the super-admin orphan management surface.
    // Optional status filter follows the same nullable-param pattern as findByMerchant.
    @Query("""
            SELECT e FROM NombaPaymentEvent e
            WHERE e.merchant IS NULL
              AND (:status IS NULL OR e.status = :status)
            ORDER BY e.createdAt DESC
            """)
    Page<NombaPaymentEvent> findOrphans(@Param("status") NombaPaymentEventStatus status, Pageable pageable);
}
