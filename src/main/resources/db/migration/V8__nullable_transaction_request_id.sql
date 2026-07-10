-- Payout transactions don't have a webhook requestId (they're created server-side, not from a
-- Nomba webhook). The V1 baseline defined this column as nullable, but ddl-auto: update previously
-- added a NOT NULL constraint on production. This migration corrects it to match the entity and
-- the V1 definition.
alter table transactions alter column request_id drop not null;
