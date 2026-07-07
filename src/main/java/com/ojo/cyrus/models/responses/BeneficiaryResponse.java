package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.Environment;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryResponse(
        UUID id,
        String nickname,
        String accountName,
        String accountNumber,
        String bankCode,
        String bankName,
        Environment environment,
        Instant createdAt
) {}
