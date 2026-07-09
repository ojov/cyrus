package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.models.entities.Payout;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Payout> findByReference(String reference);

    Page<Payout> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    /**
     * Pessimistic-write lock on the payout row, keyed by its reference (Nomba's {@code merchantTxRef}).
     * Used when a payout webhook finalizes the payout: it serializes the async webhook against the
     * synchronous initiate path so they can't both flip status / double-refund the wallet. Re-check
     * the status after acquiring the lock (a duplicate/late webhook may find it already terminal).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.reference = :reference")
    Optional<Payout> findByReferenceForUpdate(@Param("reference") String reference);

    /**
     * Payouts in PROCESSING with a known provider reference — the synchronous transfer response
     * included a provider ID, but the completing webhook (payout_success / payout_failed) hasn't
     * arrived yet. These are candidates for the requery sweep: we can ask Nomba directly.
     */
    List<Payout> findByStatusAndProviderReferenceIsNotNull(PayoutStatus status);

    // Platform oversight: payouts that were accepted by Nomba but whose completing webhook never
    // arrived — stuck (wallet already debited). Surfaced for admin triage.
    long countByStatus(PayoutStatus status);

    List<Payout> findByStatusOrderByCreatedAtDesc(PayoutStatus status);
}
