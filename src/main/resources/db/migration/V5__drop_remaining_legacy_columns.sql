-- Final sweep of pre-single-account-pivot leftover columns (same root cause as V3/V4: prod evolved
-- via ddl-auto=update from the OLD entity model, which never drops columns; V1__baseline.sql was
-- reverse-engineered from dev's already-rebuilt DB, so it never captured these). Enumerated by
-- diffing the pre-pivot entities (8d17bcf~1) against the current ones, so this covers every remaining
-- NOT-NULL leftover on the two tables that KEPT their name across the pivot (virtual_accounts,
-- transactions) — the renamed tables (merchant_customers, nomba_payment_events) got fresh, clean
-- schemas and are unaffected.

-- virtual_accounts: old entity had merchant_id + customer_id (both NOT NULL FKs) and environment
-- (NOT NULL). The new VirtualAccount binds only to merchant_customer_id, so an insert sets none of
-- these -> not-null violation. This is what blocks POST /v1/customers in production today.
alter table virtual_accounts drop column if exists merchant_id;
alter table virtual_accounts drop column if exists customer_id;
alter table virtual_accounts drop column if exists environment;

-- transactions: provider_transaction_id and payment_event_id were NOT NULL in the old model but are
-- nullable now (a PAYOUT/REVERSAL has neither a provider tx id nor an inbound payment event). Inbound
-- CUSTOMER_PAYMENT inserts already supply both, so relaxing is strictly more permissive and cannot
-- break the working reconciliation path; it just unblocks payouts. environment is a dead NOT-NULL
-- (default 'TEST') column the new model dropped entirely.
alter table transactions alter column provider_transaction_id drop not null;
alter table transactions alter column payment_event_id drop not null;
alter table transactions drop column if exists environment;
