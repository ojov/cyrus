package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.entities.NombaPaymentEvent;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.repositories.NombaPaymentEventRepository;
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

    private final NombaPaymentEventRepository paymentEventRepository;

    /**
     * Records a raw Nomba webhook event with the fields extracted from the normalized payload.
     * Merchant + virtual-account attribution is filled in later (by ingestion) once the credited
     * account number resolves; for an orphan payment both stay null.
     */
    @Transactional
    public NombaPaymentEvent recordEvent(NormalizedPaymentEvent event, String rawPayload) {
        log.debug("Recording Nomba payment event: requestId={}, type={}", event.getRequestId(), event.getEventType());
        NombaPaymentEvent entity = NombaPaymentEvent.builder()
                .requestId(event.getRequestId())
                .eventType(NombaPaymentEventType.fromWire(event.getEventType()))
                .transactionId(event.getProviderTransactionId())
                .sessionId(event.getSessionId())
                .accountNumber(event.getVirtualAccountNumber())
                .amount(event.getAmount())
                .fee(event.getFee())
                .providerWalletId(event.getWalletId())
                .status(NombaPaymentEventStatus.RECEIVED)
                .rawPayload(rawPayload)
                .build();
        return paymentEventRepository.save(entity);
    }

    /**
     * Records a minimal event when the payload could not be normalized (bad shape) — enough to keep
     * an idempotent, inspectable record without a full attribution.
     */
    @Transactional
    public NombaPaymentEvent recordUnparseable(String requestId, String rawEventType, String rawPayload) {
        NombaPaymentEvent entity = NombaPaymentEvent.builder()
                .requestId(requestId)
                .eventType(NombaPaymentEventType.fromWire(rawEventType))
                .status(NombaPaymentEventStatus.RECEIVED)
                .rawPayload(rawPayload)
                .build();
        return paymentEventRepository.save(entity);
    }

    public Optional<NombaPaymentEvent> findByRequestId(String requestId) {
        return paymentEventRepository.findByRequestId(requestId);
    }

    /**
     * Ownership-checked lookup — a merchant can only ever see their own events. Not found and
     * not-yours look identical (404) so a merchant can't distinguish "doesn't exist" from
     * "belongs to someone else" by probing IDs.
     */
    public NombaPaymentEvent findByIdForMerchant(UUID merchantId, UUID id) {
        NombaPaymentEvent event = paymentEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment event not found"));
        if (event.getMerchant() == null || !event.getMerchant().getId().equals(merchantId)) {
            throw new EntityNotFoundException("Payment event not found");
        }
        return event;
    }

    public PaymentEventResponse getForMerchant(UUID merchantId, UUID id) {
        return Mapper.toPaymentEventResponse(findByIdForMerchant(merchantId, id));
    }

    /**
     * List a merchant's own events with pagination and optional status filter. Events with no
     * resolved merchant (fully unattributable orphans) are never visible here.
     */
    public Page<PaymentEventListItem> listEvents(UUID merchantId, NombaPaymentEventStatus status, Pageable pageable) {
        return paymentEventRepository.findByMerchant(merchantId, status, pageable)
                .map(Mapper::toPaymentEventListItem);
    }

    @Transactional
    public void updateStatus(UUID id, NombaPaymentEventStatus status, String details) {
        updateStatus(id, status, null, details);
    }

    @Transactional
    public void updateStatus(UUID id, NombaPaymentEventStatus status, ReconciliationFailureReason reason, String details) {
        NombaPaymentEvent event = paymentEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("NombaPaymentEvent not found: " + id));
        event.setStatus(status);
        event.setStatusDetails(details);
        if (reason != null) {
            event.setFailureReason(reason);
        }
    }
}
