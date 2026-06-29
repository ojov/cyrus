package com.ojo.cyrus.models.responses;

import java.util.UUID;

public record LoginResponse(
        String token,
        String tokenType,
        UUID merchantId,
        String businessName,
        String businessEmail
) {}
