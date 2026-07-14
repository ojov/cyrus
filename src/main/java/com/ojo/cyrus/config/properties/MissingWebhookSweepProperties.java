package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config for {@link com.ojo.cyrus.services.MissingWebhookSweepService} — periodically diffs Nomba's
 * sub-account transaction list against local records to catch a payment whose webhook was never
 * delivered at all (see AGENTS.md's "known gap" note on missing-webhook detection).
 *
 * @param delaySeconds  how often the sweep runs
 * @param lookbackHours trailing window pulled from Nomba each run — deliberately overlaps the
 *                      previous run's window so a transaction can never fall in the gap between sweeps
 * @param dryRun        when true (the default), a detected gap is only logged (with the raw item
 *                      payload) — nothing is persisted. The field mapping below is unverified against
 *                      a real Nomba response (the docs page didn't enumerate the full item schema);
 *                      dry-run output is meant to be eyeballed before this is ever flipped to write.
 * @param logSampleItem when true, the first item scanned each run is logged in full regardless of
 *                      whether it's a gap — a verification aid for inspecting the real response
 *                      schema. Defaults false: the raw item contains payer PII and the schema is
 *                      already verified; enable via env only when debugging.
 */
@ConfigurationProperties(prefix = "app.missing-webhook-sweep")
public record MissingWebhookSweepProperties(
        @DefaultValue("21600") long delaySeconds,
        @DefaultValue("48") long lookbackHours,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("false") boolean logSampleItem
) {}
