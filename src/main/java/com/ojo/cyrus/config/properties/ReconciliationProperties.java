package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.reconciliation")
public record ReconciliationProperties(
        // How often the sweep (ReconciliationService.sweepPendingReconciliations) runs, and the
        // minimum age a still-unmatched transaction must have before the sweep retries it — first
        // attempt happens immediately on ingestion (reconcileAsync), this only governs the fallback
        // retries for transactions Nomba hadn't confirmed yet.
        @DefaultValue("300") long delaySeconds,
        // How many requery attempts (immediate + sweep retries) before we give up and flag the
        // transaction MANUAL_REVIEW instead of retrying forever.
        @DefaultValue("5") int maxAttempts
) {}
