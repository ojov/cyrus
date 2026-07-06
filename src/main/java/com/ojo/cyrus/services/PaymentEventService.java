package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.nomba.NombaWebhookAdapter;
import com.ojo.cyrus.repositories.PaymentEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService {

    private final PaymentEventRepository paymentEventRepository;
    private final NombaWebhookAdapter nombaAdapter;

    /**
     * Records a raw webhook event.
     */
    @Transactional
    public PaymentEvent recordEvent(String requestId, Provider provider, String eventType, String payload) {
        log.debug("Recording payment event: requestId={}, provider={}, type={}", requestId, provider, eventType);
        
        PaymentEvent event = PaymentEvent.builder()
                .requestId(requestId)
                .provider(provider)
                .eventType(eventType)
                .payload(payload)
                .status(EventStatus.PENDING)
                .build();
        
        return paymentEventRepository.save(event);
    }


    /**
     * Finds an event by its unique ID.
     */
    public Optional<PaymentEvent> findById(UUID id) {
        return paymentEventRepository.findById(id);
    }

    /**
     * Finds an event by its provider-specific request ID.
     */
    public Optional<PaymentEvent> findByRequestId(String requestId) {
        return paymentEventRepository.findByRequestId(requestId);
    }

    public PaymentEvent getByRequestId(String requestId) {
        return paymentEventRepository.findByRequestId(requestId)
                .orElseThrow(() -> new EntityNotFoundException("PaymentEvent not found: " + requestId));
    }

    /**
     * Checks if an event with the given requestId already exists.
     */
    public boolean existsByRequestId(String requestId) {
        return paymentEventRepository.existsByRequestId(requestId);
    }

    /**
     * List events with pagination and optional filtering by status or provider.
     */
    public Page<PaymentEvent> listEvents(EventStatus status, Provider provider, Pageable pageable) {
        PaymentEvent probe = PaymentEvent.builder()
                .status(status)
                .provider(provider)
                .build();
        return paymentEventRepository.findAll(Example.of(probe), pageable);
    }


    /**
     * Updates the status of an event.
     */
    @Transactional
    public void updateStatus(UUID id, EventStatus status, String details) {
        PaymentEvent event = paymentEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentEvent not found: " + id));
        
        event.setStatus(status);
        event.setStatusDetails(details);
        paymentEventRepository.save(event);
    }
}
