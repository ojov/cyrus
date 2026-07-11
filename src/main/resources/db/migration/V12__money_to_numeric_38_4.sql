-- Money moves from whole-kobo integers (numeric(38)) to kobo with sub-kobo precision
-- (numeric(38,4)). Scale-only widening: every existing value maps exactly to N.0000, so no
-- rounding can occur. Each ALTER rewrites its table under an exclusive lock (tables are small;
-- run with the app stopped). fee_config.inflow_percent is already numeric(10,4) — untouched.

ALTER TABLE nomba_payment_events
    ALTER COLUMN amount TYPE numeric(38, 4),
    ALTER COLUMN fee    TYPE numeric(38, 4);

ALTER TABLE transactions
    ALTER COLUMN amount            TYPE numeric(38, 4),
    ALTER COLUMN fee               TYPE numeric(38, 4),
    ALTER COLUMN platform_fee_kobo TYPE numeric(38, 4),
    ALTER COLUMN merchant_fee_kobo TYPE numeric(38, 4);

ALTER TABLE payouts
    ALTER COLUMN amount TYPE numeric(38, 4),
    ALTER COLUMN fee    TYPE numeric(38, 4);

ALTER TABLE wallets
    ALTER COLUMN available_balance TYPE numeric(38, 4);

ALTER TABLE ledger_entries
    ALTER COLUMN amount TYPE numeric(38, 4);

ALTER TABLE fee_config
    ALTER COLUMN inflow_min_kobo      TYPE numeric(38, 4),
    ALTER COLUMN inflow_max_kobo      TYPE numeric(38, 4),
    ALTER COLUMN payout_flat_fee_kobo TYPE numeric(38, 4);
