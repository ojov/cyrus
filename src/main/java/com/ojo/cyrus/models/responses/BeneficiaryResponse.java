package com.ojo.cyrus.models.responses;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryResponse(
        UUID id,
        String nickname,
        String accountName,
        String accountNumber,
        String bankCode,
        String bankName,
        Instant createdAt
) {}
