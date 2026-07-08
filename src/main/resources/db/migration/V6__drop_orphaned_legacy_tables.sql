-- Cleanup (not correctness): remove schema objects no current entity maps, left behind by the
-- single-account pivot's table renames / restructures. ddl-auto=update created the new tables but
-- never dropped the old ones. None of these are referenced by current code (verified by diffing
-- current entities against the pre-pivot ones); ddl-auto: validate ignores unmapped tables/columns,
-- which is why they lingered silently. CASCADE only removes each table plus FK constraints that
-- point AT it — such constraints reference dead data and can't guard a live insert — so active
-- tables and their rows are untouched. IF EXISTS makes every statement a no-op if already absent.

-- Dead nullable column: MerchantWebhookEvent dropped its `environment` field in the pivot. It was
-- nullable (never a not-null blocker), just dead.
alter table merchant_webhook_events drop column if exists environment;

-- Renamed entities -> new tables; the old ones are orphaned:
--   Customer      -> merchant_customers   (old: customers)
--   PaymentEvent  -> nomba_payment_events (old: payment_events)
drop table if exists customers cascade;
drop table if exists payment_events cascade;

-- Merchant.webhookConfig went from an @ElementCollection Map<Environment, WebhookConfig> (its own
-- table) to a single @Embedded WebhookConfig (url + encrypted_secret columns ON merchants). The old
-- collection table is orphaned.
drop table if exists merchant_webhook_configs cascade;

-- Element-collection tables from the retired per-merchant multi-Nomba-credential model. V3 already
-- dropped these; re-assert defensively (harmless no-op if V3's names matched and they're gone).
drop table if exists merchant_nomba_credentials cascade;
drop table if exists merchants_nomba_sub_account_ids cascade;
