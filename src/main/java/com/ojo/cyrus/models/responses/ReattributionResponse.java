package com.ojo.cyrus.models.responses;

import java.util.UUID;

public record ReattributionResponse(
        UUID transactionId,
        String customerReference
) {}
