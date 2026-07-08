-- The pre-single-account-pivot Merchant entity (before 8d17bcf) stored per-merchant Nomba
-- credentials (client id/secret per environment, via merchant_nomba_credentials) and a
-- parent/sub Nomba account id. The single-account refactor removed all of these fields from the
-- Merchant entity, but ddl-auto=update never drops columns/tables, and V1__baseline.sql was
-- reverse-engineered from dev's DB (which never carried these), not prod's — so prod silently
-- kept the NOT NULL nomba_parent_account_id column. Every merchant registration since the pivot
-- has failed in production with a not-null constraint violation, because Hibernate's generated
-- INSERT for the current entity never sets it.
alter table merchants drop column if exists nomba_parent_account_id;

-- Orphaned @ElementCollection tables from the same removed fields — nothing in code has
-- referenced these since the pivot.
drop table if exists merchant_nomba_credentials;
drop table if exists merchants_nomba_sub_account_ids;
