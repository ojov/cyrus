package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.repositories.PaymentEventRepository;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    /**
     * Records a raw webhook event. {@code merchant} is the best-effort owner resolved from the
     * payload's wallet id — null when the wallet is unrecognized too (a fully unattributable event).
     */
    @Transactional
    public PaymentEvent recordEvent(String requestId, Provider provider, String eventType, String payload,
                                     Merchant merchant) {
        log.debug("Recording payment event: requestId={}, provider={}, type={}", requestId, provider, eventType);

        PaymentEvent event = PaymentEvent.builder()
                .requestId(requestId)
                .provider(provider)
                .eventType(eventType)
                .payload(payload)
                .merchant(merchant)
                .status(EventStatus.PENDING)
                .build();

        return paymentEventRepository.save(event);
    }

    /**
     * Finds an event by its provider-specific request ID.
     */
    public Optional<PaymentEvent> findByRequestId(String requestId) {
        return paymentEventRepository.findByRequestId(requestId);
    }

    /**
     * Ownership-checked lookup — a merchant can only ever see their own events. Not found and
     * not-yours look identical (404) so a merchant can't distinguish "doesn't exist" from
     * "belongs to someone else" by probing IDs.
     */
    public PaymentEvent findByIdForMerchant(UUID merchantId, UUID id) {
        PaymentEvent event = paymentEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment event not found"));
        if (event.getMerchant() == null || !event.getMerchant().getId().equals(merchantId)) {
            throw new EntityNotFoundException("Payment event not found");
        }
        return event;
    }

    /**
     * The ownership-checked lookup above, mapped to the API-facing detail DTO (includes the raw
     * payload, useful for inspecting exactly what Nomba sent before deciding whether to reattribute).
     */
    public PaymentEventResponse getForMerchant(UUID merchantId, UUID id) {
        return Mapper.toPaymentEventResponse(findByIdForMerchant(merchantId, id));
    }

    /**
     * List a merchant's own events with pagination and optional filtering by status or provider.
     * Events with no resolved merchant (fully unattributable) are never visible here.
     */
    public Page<PaymentEventListItem> listEvents(UUID merchantId, EventStatus status, Provider provider, Pageable pageable) {
        return paymentEventRepository.findByMerchant(merchantId, status, provider, pageable)
                .map(Mapper::toPaymentEventListItem);
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
