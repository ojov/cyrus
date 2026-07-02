package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateSubAccountsRequest(
        @Schema(description = "Complete set of Nomba sub-account IDs for this merchant")
        @NotEmpty(message = "At least one sub-account ID is required")
        Set<String> subAccountIds
) {}
