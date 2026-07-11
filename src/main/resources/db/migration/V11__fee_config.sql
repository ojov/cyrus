-- Single-row fee configuration table. Seeded with the platform defaults (1.5% inflow,
-- min ₦15, max ₦225, ₦30 flat payout fee). Super-admins update this via the API;
-- FeeConfigService loads it into FeeProperties at startup and on every write.
create table fee_config
(
    id                     uuid                        not null
        primary key,
    created_at             timestamp(6) with time zone not null,
    updated_at             timestamp(6) with time zone,
    inflow_percent         numeric(10, 4)              not null,
    inflow_min_kobo        numeric(38)                 not null,
    inflow_max_kobo        numeric(38)                 not null,
    payout_flat_fee_kobo   numeric(38)                 not null,
    version                bigint                      not null
);

insert into fee_config (id, created_at, updated_at, inflow_percent, inflow_min_kobo, inflow_max_kobo, payout_flat_fee_kobo, version)
values ('00000000-0000-0000-0000-000000000001', now(), now(), 1.5, 1500, 22500, 3000, 0);
