-- Three entity fields were mapped @Lob, which Hibernate/Postgres store as an `oid` (large object
-- reference), not the actual bytes inline in the row. Reading an oid column back out during a bulk
-- multi-row query (e.g. GET /v1/admin/payment-events) fails with "Unable to access lob stream" —
-- found live while auditing nomba_payment_events.raw_payload. Converting all three to plain `text`
-- (matching transactions.raw_payload, which never used @Lob) fixes it. lo_get() reads out each large
-- object's actual content before the oid column (and the underlying large objects) are dropped;
-- lo_unlink() releases the large objects themselves, which lo_get() alone does not do.

ALTER TABLE nomba_payment_events ADD COLUMN raw_payload_text TEXT;
UPDATE nomba_payment_events
SET raw_payload_text = convert_from(lo_get(raw_payload), 'UTF8')
WHERE raw_payload IS NOT NULL;
SELECT lo_unlink(raw_payload) FROM nomba_payment_events WHERE raw_payload IS NOT NULL;
ALTER TABLE nomba_payment_events DROP COLUMN raw_payload;
ALTER TABLE nomba_payment_events RENAME COLUMN raw_payload_text TO raw_payload;

ALTER TABLE merchant_customers ADD COLUMN metadata_text TEXT;
UPDATE merchant_customers
SET metadata_text = convert_from(lo_get(metadata), 'UTF8')
WHERE metadata IS NOT NULL;
SELECT lo_unlink(metadata) FROM merchant_customers WHERE metadata IS NOT NULL;
ALTER TABLE merchant_customers DROP COLUMN metadata;
ALTER TABLE merchant_customers RENAME COLUMN metadata_text TO metadata;

ALTER TABLE webhook_deliveries ADD COLUMN response_body_text TEXT;
UPDATE webhook_deliveries
SET response_body_text = convert_from(lo_get(response_body), 'UTF8')
WHERE response_body IS NOT NULL;
SELECT lo_unlink(response_body) FROM webhook_deliveries WHERE response_body IS NOT NULL;
ALTER TABLE webhook_deliveries DROP COLUMN response_body;
ALTER TABLE webhook_deliveries RENAME COLUMN response_body_text TO response_body;
