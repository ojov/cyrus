package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    boolean existsByRequestId(String requestId);
    Optional<PaymentEvent> findByRequestId(String requestId);
}
