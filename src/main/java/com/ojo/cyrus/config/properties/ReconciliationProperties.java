package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.reconciliation")
public record ReconciliationProperties(
        // How long after a webhook arrives (and between each subsequent retry) before we requery
        // Nomba to confirm it — gives a genuine transfer time to settle before we check.
        @DefaultValue("300") long delaySeconds,
        // How many times we requery a session Nomba hasn't confirmed yet before giving up and
        // flagging the transaction MANUAL_REVIEW instead of retrying forever.
        @DefaultValue("5") int maxAttempts
) {}
