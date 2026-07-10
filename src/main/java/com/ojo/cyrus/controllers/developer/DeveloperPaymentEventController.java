package com.ojo.cyrus.controllers.developer;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.ReattributePaymentEventRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.models.responses.ReattributionResponse;
import com.ojo.cyrus.services.PaymentEventService;
import com.ojo.cyrus.services.TransactionIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payment-events")
@RequiredArgsConstructor
@Tag(name = "Payment Events (Developer)", description = "Your raw inbound payment events — includes orphan/misdirected payments " +
        "that could not be automatically attributed to a customer. Use the reattribute endpoint to fix them.")
public class DeveloperPaymentEventController {

    private final PaymentEventService paymentEventService;
    private final TransactionIngestionService ingestionService;

    @Operation(
            summary = "List your payment events",
            description = "Paginated list of your own payment events with optional status filtering. " +
                    "Orphans (payments that couldn't be attributed to a customer) show up here with " +
                    "status=IGNORED — use the reattribute endpoint to fix them.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping
    public CyrusApiResponse<Page<PaymentEventListItem>> listEvents(
            @AuthenticationPrincipal Merchant merchant,
            @RequestParam(required = false) NombaPaymentEventStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment events retrieved",
                paymentEventService.listEvents(merchant.getId(), status, pageable));
    }

    @Operation(
            summary = "Get a payment event",
            description = "Retrieve a single payment event by ID, including the raw provider payload.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping("/{id}")
    public CyrusApiResponse<PaymentEventResponse> getEvent(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable UUID id) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event retrieved",
                paymentEventService.getForMerchant(merchant.getId(), id));
    }

    @Operation(
            summary = "Replay a payment event",
            description = "Manually re-trigger the ingestion pipeline for a specific payment event.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PostMapping("/{id}/replay")
    public CyrusApiResponse<Void> replayEvent(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable UUID id) {

        ingestionService.replayEvent(merchant.getId(), id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event replay triggered", null);
    }

    @Operation(
            summary = "Reattribute a misdirected payment",
            description = "Manually attributes an orphaned (IGNORED) payment event to one of your customers. " +
                    "Use this when a payment's provider payload doesn't resolve to a known virtual account. " +
                    "Mints a transaction against the chosen customer and runs reconciliation.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PostMapping("/{id}/reattribute")
    public CyrusApiResponse<ReattributionResponse> reattribute(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable UUID id,
            @Valid @RequestBody ReattributePaymentEventRequest request) {

        Transaction tx = ingestionService.reattribute(merchant.getId(), id, request.customerReference());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payment event reattributed",
                new ReattributionResponse(tx.getId(), request.customerReference()));
    }
}
