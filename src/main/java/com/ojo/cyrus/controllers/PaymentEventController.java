package com.ojo.cyrus.controllers;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.PaymentEventService;
import com.ojo.cyrus.services.TransactionIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/payment-events")
@RequiredArgsConstructor
@Tag(name = "Payment Events Admin", description = "Management endpoints for inbound payment events (webhooks).")
@Slf4j
public class PaymentEventController {

    private final PaymentEventService paymentEventService;
    private final TransactionIngestionService ingestionService;

    @Operation(summary = "List payment events", description = "Retrieve a paginated list of payment events with optional filtering.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CyrusApiResponse<Page<PaymentEvent>> listEvents(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Provider provider,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<PaymentEvent> events = paymentEventService.listEvents(status, provider, pageable);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment events retrieved", events);
    }

    @Operation(summary = "Get payment event details", description = "Retrieve a single payment event by its ID.")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CyrusApiResponse<PaymentEvent> getEvent(@PathVariable UUID id) {
        return paymentEventService.findById(id)
                .map(event -> CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event retrieved", event))
                .orElseGet(() -> CyrusApiResponse.failure(ResponseCode.RESOURCE_NOT_FOUND, "Payment event not found"));
    }

    @Operation(summary = "Replay payment event", description = "Manually trigger the ingestion process for a specific payment event.")
    @PostMapping("/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public CyrusApiResponse<Void> replayEvent(@PathVariable UUID id) {
        ingestionService.replayEvent(id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event replay triggered", null);
    }
}
