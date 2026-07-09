package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.ReattributeOrphanRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.models.responses.ReattributionResponse;
import com.ojo.cyrus.services.PaymentEventService;
import com.ojo.cyrus.services.PlatformAdminService;
import com.ojo.cyrus.services.TransactionIngestionService;
import com.ojo.cyrus.utils.Mapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/platform/orphans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform Orphans (super-admin)",
        description = "List, inspect, and reattribute fully-unattributable payment events " +
                "(those whose credited account number matched no known virtual account).")
public class PlatformOrphanController {

    private final PlatformAdminService platformAdminService;
    private final PaymentEventService paymentEventService;
    private final TransactionIngestionService ingestionService;

    @Operation(
            summary = "List orphans",
            description = "Paginated list of payment events with no owning merchant. Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<Page<PaymentEventListItem>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) NombaPaymentEventStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        String email = jwt.getSubject();
        platformAdminService.requireSuperAdmin(email);
        log.info("Super-admin {} listing orphans (status={})", email, status);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Orphans retrieved",
                paymentEventService.listOrphans(status, pageable));
    }

    @Operation(
            summary = "Orphan detail",
            description = "Full detail of a single orphan event, including the raw provider payload. Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{id}")
    public CyrusApiResponse<PaymentEventResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        String email = jwt.getSubject();
        platformAdminService.requireSuperAdmin(email);
        log.info("Super-admin {} viewing orphan {}", email, id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Orphan retrieved",
                Mapper.toPaymentEventResponse(paymentEventService.getById(id)));
    }

    @Operation(
            summary = "Reattribute orphan",
            description = "Manually attribute an orphan payment event to a merchant's customer. " +
                    "The super-admin specifies both the target merchant and the customer reference. " +
                    "Mints a transaction and feeds it into reconciliation, same as normal ingestion.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/{id}/reattribute")
    public CyrusApiResponse<ReattributionResponse> reattribute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReattributeOrphanRequest request) {
        String email = jwt.getSubject();
        platformAdminService.requireSuperAdmin(email);
        log.info("Super-admin {} reattributing orphan {} to merchant={} / customer={}",
                email, id, request.merchantId(), request.customerReference());
        Transaction tx = ingestionService.reattributeOrphan(
                request.merchantId(), id, request.customerReference());
        log.info("Orphan {} reattributed as tx {} to merchant={} / customer={}",
                id, tx.getId(), request.merchantId(), request.customerReference());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Orphan reattributed",
                new ReattributionResponse(tx.getId(), request.customerReference()));
    }
}
