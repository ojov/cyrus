package com.ojo.cyrus.models.requests;

import com.ojo.cyrus.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Sets the customer's (and their virtual account's) status. ACTIVE and SUSPENDED are freely
 * reversible — SUSPENDED disables the virtual account without losing anything. CLOSED is
 * terminal (the soft-delete state): the customer, virtual account, and full transaction history
 * stay intact and queryable, but no further status transitions are accepted.
 */
public record UpdateCustomerStatusRequest(
        @Schema(description = "The status to set")
        @NotNull(message = "Status is required")
        CustomerStatus status
) {}
