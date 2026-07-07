package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Cyrus's revenue model on inbound payments: cost-plus markup on Nomba's own confirmed fee.
 * {@code markupMultiplier} is applied to the fee Nomba actually deducted (confirmed at
 * reconciliation, not the webhook's estimate) to get the total fee Cyrus charges the merchant;
 * Cyrus's margin is the delta between the two. E.g. {@code 1.30} → Nomba fee ₦10 becomes a ₦13
 * platform fee (₦10 passed through, ₦3 margin).
 */
@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(
        @DefaultValue("1.30") BigDecimal markupMultiplier
) {}
