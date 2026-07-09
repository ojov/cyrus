package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.CreateBeneficiaryRequest;
import com.ojo.cyrus.models.requests.VerifyBeneficiaryRequest;
import com.ojo.cyrus.models.responses.AccountVerificationResponse;
import com.ojo.cyrus.models.responses.BankResponse;
import com.ojo.cyrus.models.responses.BeneficiaryResponse;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.BeneficiaryService;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/me/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Beneficiaries", description = "Bank accounts you can pay out to. Verified against Nomba on registration.")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final MerchantService merchantService;

    @Operation(summary = "Verify a destination account",
            description = "Resolves the account holder name for an account number + bank, so the merchant can " +
                    "confirm it before adding the beneficiary. Returns 422 if the account can't be verified.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/verify")
    public CyrusApiResponse<AccountVerificationResponse> verify(
            @Valid @RequestBody VerifyBeneficiaryRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Account verified",
                beneficiaryService.verifyAccount(request.accountNumber(), request.bankCode()));
    }

    @Operation(summary = "Register a beneficiary",
            description = "Adds a bank account for payouts. The account is verified against the provider as a hard " +
                    "gate — an unverifiable account is rejected (422), never stored. The label is the verified name.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping
    public CyrusApiResponse<BeneficiaryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBeneficiaryRequest request) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.CREATED, "Beneficiary registered",
                beneficiaryService.create(merchantId, request));
    }

    @Operation(summary = "List beneficiaries",
            description = "Lists registered beneficiaries.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<List<BeneficiaryResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Beneficiaries retrieved",
                beneficiaryService.list(merchantId));
    }

    @Operation(summary = "List payable banks",
            description = "The banks and NIP codes to pick from when registering a beneficiary — select a bank " +
                    "here rather than hand-typing a code, so it's guaranteed to be one Nomba actually recognizes.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/banks")
    public CyrusApiResponse<List<BankResponse>> listBanks() {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Banks retrieved", beneficiaryService.listBanks());
    }
}
