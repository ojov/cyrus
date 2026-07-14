-- Adds MISSING_WEBHOOK to nomba_payment_events.failure_reason's allowed values, for orphan events
-- created by MissingWebhookSweepService (a Nomba-side transaction with no matching local webhook
-- record at all). The Java enum is what ddl-auto: validate checks against; this CHECK constraint is
-- a separate, hand-maintained mirror of it that does not auto-update — see AGENTS.md's note on the
-- NombaPaymentEventType CHECK-constraint gotcha for the same pattern.
alter table nomba_payment_events
    drop constraint nomba_payment_events_failure_reason_check;

alter table nomba_payment_events
    add constraint nomba_payment_events_failure_reason_check
        check ((failure_reason)::text = ANY
               ((ARRAY ['UNKNOWN_VIRTUAL_ACCOUNT'::character varying, 'INACTIVE_CUSTOMER'::character varying, 'NON_CREDIT_EVENT'::character varying, 'DUPLICATE'::character varying, 'SIGNATURE_MISMATCH'::character varying, 'AMOUNT_MISMATCH'::character varying, 'PROVIDER_UNCONFIRMED'::character varying, 'MISSING_WEBHOOK'::character varying])::text[]));
