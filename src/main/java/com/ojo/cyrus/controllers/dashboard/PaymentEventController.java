package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.ReattributePaymentEventRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.models.responses.ReattributionResponse;
import com.ojo.cyrus.services.MerchantService;
import com.ojo.cyrus.services.PaymentEventService;
import com.ojo.cyrus.services.TransactionIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/payment-events")
@RequiredArgsConstructor
@Tag(name = "Payment Events", description = "Your own raw inbound payment events (webhooks) — includes orphan/misdirected payments awaiting reattribution.")
@Slf4j
public class PaymentEventController {

    private final PaymentEventService paymentEventService;
    private final TransactionIngestionService ingestionService;
    private final MerchantService merchantService;

    @Operation(summary = "List payment events",
            description = "Retrieve a paginated list of your own payment events, with optional status filtering.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<Page<PaymentEventListItem>> listEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) NombaPaymentEventStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment events retrieved",
                paymentEventService.listEvents(merchantId, status, pageable));
    }

    @Operation(summary = "Get payment event details",
            description = "Retrieve a single payment event by its ID, including the raw provider payload.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{id}")
    public CyrusApiResponse<PaymentEventResponse> getEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event retrieved",
                paymentEventService.getForMerchant(merchantId, id));
    }

    @Operation(summary = "Replay payment event",
            description = "Manually trigger the ingestion process for a specific payment event.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/{id}/replay")
    public CyrusApiResponse<Void> replayEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        ingestionService.replayEvent(merchantId, id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event replay triggered", null);
    }

    @Operation(summary = "Reattribute a misdirected payment",
            description = "Manually attributes an orphaned (IGNORED) payment event to one of your customers, " +
                    "for a payment whose provider payload doesn't resolve to a known virtual account. Mints a " +
                    "transaction against the chosen customer and feeds it into the normal reconciliation pipeline.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/{id}/reattribute")
    public CyrusApiResponse<ReattributionResponse> reattribute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReattributePaymentEventRequest request) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        Transaction tx = ingestionService.reattribute(merchantId, id, request.customerReference());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event reattributed",
                new ReattributionResponse(tx.getId(), request.customerReference()));
    }
}
