-- Platform profit ledger: append-only entries tracking aggregate fund movements through Cyrus's
-- Nomba account. The running total (SUM of amount_kobo) is the expected provider balance from
-- Cyrus's perspective, reconciled against the live Nomba balance via a scheduled sweep.

CREATE TABLE platform_profit_entries
(
    id             uuid                        NOT NULL PRIMARY KEY,
    created_at     timestamp(6) with time zone NOT NULL,
    updated_at     timestamp(6) with time zone,
    transaction_id uuid,
    payout_id      uuid,
    entry_type     varchar(50)                 NOT NULL,
    amount_kobo    numeric(38, 4)              NOT NULL,
    description    varchar(500),

    CONSTRAINT fk_ppe_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ppe_payout FOREIGN KEY (payout_id) REFERENCES payouts (id)
);

CREATE INDEX idx_ppe_transaction ON platform_profit_entries (transaction_id);
CREATE INDEX idx_ppe_payout ON platform_profit_entries (payout_id);
CREATE INDEX idx_ppe_type ON platform_profit_entries (entry_type);
CREATE INDEX idx_ppe_created ON platform_profit_entries (created_at);
