-- Adds the authorization role for platform RBAC. Every existing (and new) merchant defaults to
-- MERCHANT; SUPER_ADMIN is granted by the config-seeded bootstrap (APP_SUPER_ADMIN_EMAILS) at
-- startup. ddl-auto is `validate`, so this column must come from a migration, not an entity edit
-- alone. The CHECK mirrors the other @Enumerated(STRING) columns' constraints in the baseline.
alter table merchants
    add column role varchar(255) not null default 'MERCHANT'
        constraint merchants_role_check check (role in ('MERCHANT', 'SUPER_ADMIN'));
