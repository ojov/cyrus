# cyrus-payments

Official Python SDK for the [Cyrus Payments API](https://api.trycyrus.app/docs) — customer payment
identity infrastructure built on Nomba. Give each of your customers a persistent identity and a
dedicated virtual bank account, and let Cyrus handle provisioning, webhook reconciliation, and
reporting.

This SDK covers the **developer (API-key) surface only** — customers, transactions, and payment
events. It does not cover the merchant dashboard (JWT-authenticated) endpoints.

## Installation

```bash
# uv (recommended)
uv add cyrus-payments

# pip
pip install cyrus-payments
```

Requires Python 3.10+.

## Quick start

```python
from cyrus import CyrusClient

client = CyrusClient(api_key="cyrus_live_xxx")

customer = client.customers.create(
    reference="cust_123",       # your own identifier for this customer (required)
    first_name="Ada",           # required
    last_name="Lovelace",       # optional
    email="ada@example.com",    # optional
)

print(customer.virtual_account.account_number, customer.virtual_account.bank_name)
```

Use it as a context manager to close the underlying HTTP connection pool automatically:

```python
with CyrusClient(api_key="cyrus_live_xxx") as client:
    customer = client.customers.get("cust_123")
```

By default the client points at production (`https://api.trycyrus.app`). Override `base_url` for a
local/staging backend:

```python
client = CyrusClient(api_key="cyrus_test_xxx", base_url="http://localhost:8080")
```

## Customers

```python
# Create
customer = client.customers.create(reference="cust_123", first_name="Ada")

# Get
customer = client.customers.get("cust_123")

# Update profile (partial — only pass what's changing)
customer = client.customers.update("cust_123", first_name="Grace", phone_number="08012345678")

# KYC tier and status
from cyrus import KycTier, CustomerStatus

client.customers.set_kyc_tier("cust_123", tier=KycTier.TIER_2)
client.customers.suspend("cust_123")     # or .reactivate(...) / .close(...)

# Statement: identity + reporting summary + paginated transaction history
statement = client.customers.statement("cust_123", page=0, size=20)
print(statement.summary.lifetime_kobo, statement.summary.transaction_count)
for row in statement.transactions.content:
    print(row.ref, row.amount_kobo, row.match_status)

# Filter by date range and/or match status
filtered = client.customers.statement(
    "cust_123",
    from_date="2025-01-01T00:00:00Z",
    to_date="2025-12-31T23:59:59Z",
    match_status=MatchStatus.DISCREPANCY,
)
```

## Transactions

Merchant-wide transaction history, across all customers:

```python
from cyrus import TransactionType, TransactionStatus, MatchStatus

page = client.transactions.list(
    customer_reference="cust_123",   # optional — omit to see all customers
    type=TransactionType.CUSTOMER_PAYMENT,
    status=TransactionStatus.SUCCESSFUL,
    match_status=MatchStatus.DISCREPANCY,
    from_date="2025-01-01T00:00:00Z",  # optional ISO-8601 instant
    to_date="2025-12-31T23:59:59Z",    # optional ISO-8601 instant
    page=0,
    size=20,
)
for txn in page:
    print(txn.reference, txn.amount_kobo, txn.fee_kobo)

transaction = client.transactions.get("txn_abc123")
```

## Payment events (exception triage)

Raw inbound payment events, including orphaned/misdirected payments that couldn't be automatically
attributed to a customer:

```python
from cyrus import PaymentEventStatus

events = client.payment_events.list(status=PaymentEventStatus.IGNORED)
event = client.payment_events.get(str(events[0].id))

client.payment_events.replay(str(event.id))
client.payment_events.reattribute(str(event.id), customer_reference="cust_123")
```

## Pagination

Every list endpoint returns a `Page`, which behaves like a list of the current page and also carries
metadata:

```python
page = client.customers.list(page=0, size=20)
list(page)                    # iterate current page's items
page.total_elements
page.total_pages
page.is_last
```

To walk every page automatically, use `iter_all(...)` (available on `customers`, `transactions`, and
`payment_events`):

```python
for customer in client.customers.iter_all(page_size=50):
    print(customer.reference)
```

## Errors

All API errors raise a subclass of `CyrusAPIError`:

```python
from cyrus import (
    CyrusAPIError,       # base class
    ValidationError,     # 400 — bad input; .field_errors has per-field details
    UnauthorizedError,   # 401 — missing/invalid API key
    NotFoundError,        # 404
    ConflictError,        # 409 — state conflict (e.g. customer already closed)
    ProviderError,        # upstream Nomba error
    ServerError,          # 5xx
)

try:
    client.customers.create(reference="cust_123", first_name="Ada", email="not-an-email")
except ValidationError as e:
    print(e.message, e.field_errors)
except CyrusAPIError as e:
    print(e.code, e.message, e.status_code)
```

Network-level failures (timeouts, connection errors) are retried automatically with exponential
backoff (`max_retries=3` by default, configurable on `CyrusClient(...)`).

## Enums

Request/response fields backed by a fixed set of values are typed enums (`CustomerStatus`,
`KycTier`, `TransactionType`, `TransactionStatus`, `MatchStatus`, `PaymentEventType`,
`PaymentEventStatus`, `ReconciliationFailureReason`), all importable from the top-level `cyrus`
package. Plain strings matching the same values also work, for callers that prefer not to import them:

```python
# equivalent — both work
client.customers.set_status("cust_123", status=CustomerStatus.SUSPENDED)
client.customers.set_status("cust_123", status="SUSPENDED")
```

## Full API reference

For the complete, always-current endpoint/schema/error reference, see the live Scalar docs:
**[https://api.trycyrus.app/docs](https://api.trycyrus.app/docs)**.

## Development

```bash
cd sdk/python
uv sync --all-extras
uv run pytest -v
```
