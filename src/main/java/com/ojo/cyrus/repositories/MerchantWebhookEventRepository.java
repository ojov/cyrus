package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.models.entities.MerchantWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MerchantWebhookEventRepository extends JpaRepository<MerchantWebhookEvent, UUID> {

    // Outbox idempotency: at most one delivery per transaction per event type.
    boolean existsByTransactionIdAndEventType(UUID transactionId, String eventType);

    // Merchant-scoped delivery history, newest first, with optional status/environment filters
    // (nullable-param style, matching the reconciliation queries).
    @Query("""
            SELECT e FROM MerchantWebhookEvent e
            WHERE e.merchant.id = :merchantId
              AND (:status IS NULL OR e.status = :status)
              AND (:environment IS NULL OR e.environment = :environment)
            ORDER BY e.createdAt DESC
            """)
    Page<MerchantWebhookEvent> findDeliveries(UUID merchantId, MerchantWebhookStatus status,
                                              Environment environment, Pageable pageable);
}
