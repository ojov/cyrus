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
@Tag(name = "Payment Events (Developer)", description = "Raw inbound payment events — includes successfully processed payments, duplicates, " +
        "and orphaned/misdirected payments that couldn't be automatically attributed to a customer. " +
        "Use this surface to triage unresolved events: reattribute orphans to the correct customer, " +
        "or replay events that the provider hadn't confirmed yet when Cyrus last checked.")
public class DeveloperPaymentEventController {

    private final PaymentEventService paymentEventService;
    private final TransactionIngestionService ingestionService;

    @Operation(
            summary = "List your payment events",
            description = "Paginated, newest-first list of raw inbound payment events belonging to your account. " +
                    "Includes successfully processed payments, ignored events, duplicates, and orphaned/misdirected " +
                    "payments (status=IGNORED, failureReason=UNKNOWN_VIRTUAL_ACCOUNT or INACTIVE_CUSTOMER). " +
                    "Use this to triage unresolved payments — filter by status to find events that need attention. " +
                    "Each row includes the amount, target account number, customer reference (if resolved), and " +
                    "the failure reason so you can decide whether to replay or reattribute without opening the detail view.",
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
            description = "Retrieve a single payment event by ID, including the raw provider payload. " +
                    "Use this to inspect exactly what the provider sent before deciding whether to replay or reattribute. " +
                    "The payload field contains the original, unmodified JSON from the provider webhook.",
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
            description = "Re-runs the reconciliation pipeline against the provider for a previously received payment event. " +
                    "Only useful when a payment is still unresolved (status=RECEIVED or FAILED) and has been sitting " +
                    "without confirmation for a while — Cyrus's automatic reconciliation may have exhausted its " +
                    "retries before the provider had the transfer ready.\n\n" +
                    "**When to use:** the event is in RECEIVED or FAILED status and the payer confirms they sent the " +
                    "money but your dashboard still shows no matching transaction. Wait at least a few minutes after " +
                    "the original payment before replaying — the provider may still be processing.\n\n" +
                    "**When NOT to use:**\n" +
                    "- Status is PROCESSED — the payment already reconciled successfully, replaying is a no-op.\n" +
                    "- Status is IGNORED — the event is a duplicate, non-credit, or orphan. Replaying won't help; " +
                    "use reattribute instead for orphans.\n" +
                    "- Status is REATTRIBUTED — already manually resolved.\n" +
                    "- As a general retry mechanism — if the provider genuinely never received the transfer, replaying " +
                    "won't create one. Contact your payer instead.\n\n" +
                    "This endpoint is idempotent — replaying an already-terminal event does nothing.",
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
                    "Only works on events with failureReason=UNKNOWN_VIRTUAL_ACCOUNT or INACTIVE_CUSTOMER — " +
                    "the event must not already have a transaction.\n\n" +
                    "Use this when:\n" +
                    "- The payer sent to a virtual account number that doesn't match any of your customers (UNKNOWN_VIRTUAL_ACCOUNT) — " +
                    "e.g. they used an old or incorrect account number.\n" +
                    "- The payment landed on a customer whose account was suspended or closed at the time (INACTIVE_CUSTOMER) — " +
                    "the payment is valid but was ignored because the customer wasn't active.\n\n" +
                    "Specifying `customerReference` is mandatory — this tells Cyrus which of your customers should own " +
                    "the transaction. The customer must be in ACTIVE status. Cyrus then mints a transaction against " +
                    "that customer and runs the normal reconciliation pipeline.",
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
