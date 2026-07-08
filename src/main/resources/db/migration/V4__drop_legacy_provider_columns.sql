-- Another orphaned column from the same pre-single-account-pivot era as V3: VirtualAccount and
-- Transaction both had a `private Provider provider;` field (multi-provider support) removed in
-- 8d17bcf when Cyrus moved to a single Nomba account. ddl-auto=update never drops columns, and
-- V1__baseline.sql was reverse-engineered from dev's DB (already past that refactor), so prod
-- silently kept virtual_accounts.provider as NOT NULL — every customer/VA creation via the API has
-- been failing in production with a not-null constraint violation. transactions.provider is included
-- defensively (IF EXISTS) in case prod carries the same leftover there, though live-verified real
-- transactions this session suggest it likely doesn't.
alter table virtual_accounts drop column if exists provider;
alter table transactions drop column if exists provider;
