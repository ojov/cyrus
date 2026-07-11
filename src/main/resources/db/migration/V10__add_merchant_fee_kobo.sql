-- Merchant-facing fee column on transactions. For CUSTOMER_PAYMENT this is the total fee charged
-- to the merchant (inflowPercent of gross, clamped); for PAYOUT it's the flat payoutFlatFeeKobo.
-- Null for old rows that predate this column — the API and webhook payload fall back to the
-- provider fee (Transaction.fee) when this is null.
ALTER TABLE transactions ADD COLUMN merchant_fee_kobo numeric(38);
