# Cyrus — Architecture & Security Note

> **Hackathon submission document.** Covers authentication, webhooks, and data handling.

---

## Architecture Overview

Cyrus is a **payment identity infrastructure layer** on top of Nomba. Merchants sign up on Cyrus,
provision virtual accounts for their customers, receive payments into those accounts, and withdraw
to bank beneficiaries — all without holding Nomba credentials.

```
┌──────────────┐     ┌─────────────────────────────────────┐     ┌─────────────┐
│   Merchant   │────▶│          Cyrus API (Spring Boot)     │────▶│  Nomba      │
│  (Developer) │     │                                     │     │  (Provider) │
│              │     │  ┌──────────┐  ┌──────────────────┐ │     │             │
│  API key     │     │  │ API-Key  │  │  JWT (Dashboard) │ │     │  VA create  │
│  or JWT      │     │  │ Chain    │  │  Chain           │ │     │  VA expire  │
│              │     │  │          │  │                  │ │     │  Transfer   │
│  POST /v1/   │     │  │ POST     │  │ /v1/auth/login   │ │     │  Webhook    │
│  customers   │     │  │ /v1/     │  │ /v1/merchants/me │ │     │  Requery    │
│  POST /v1/   │     │  │ customers│  │ /ops/* frontend  │ │     └─────────────┘
│  payouts     │     │  │ /v1/     │  └──────────────────┘ │
│              │     │  │ transact.│                       │
│              │     │  └──────────┘                       │
│              │     │                                     │
│              │     │  ┌─────────────────────────────────┐ │
│              │     │  │  Reconciliation Engine          │ │
│              │     │  │  ├─ @Async requery on ingest    │ │
│              │     │  │  └─ @Scheduled sweep fallback   │ │
│              │     │  └─────────────────────────────────┘ │
│              │     │                                     │
│              │     │  ┌─────────────────────────────────┐ │
│              │     │  │  Wallet (double-entry ledger)   │ │
│              │     │  │  ├─ Per-merchant wallet         │ │
│              │     │  │  └─ Append-only LedgerEntry     │ │
│              │     │  └─────────────────────────────────┘ │
│              │     │                                     │
│              │     │  ┌─────────────────────────────────┐ │
│              │     │  │  Outbound Webhook Dispatcher    │ │
│              │     │  │  (JobRunr, exponential backoff) │ │
│              │     │  └─────────────────────────────────┘ │
│              │     └─────────────────────────────────────┘ │
│              │                                            │
│              ▼                                            │
│     ┌──────────────┐                                     │
│     │  Merchant's   │◀──── payment.succeeded webhook     │
│     │  Application  │     (HMAC-signed)                  │
│     └──────────────┘                                     │
└───────────────────────────────────────────────────────────┘
```

### Two Security Filter Chains

The API routes through one of two Spring Security filter chains:

| Chain | Routes | Auth Method | Principal |
|---|---|---|---|
| **API-key chain** (`@Order(1)`) | `/v1/customers/**`, `/v1/transactions/**`, `/v1/payment-events/**` | `Authorization: Bearer cyrus_...` | The owning `Merchant` |
| **JWT chain** (default) | Everything else — `/v1/merchants/me/**`, `/v1/admin/**` | JWT (RSA-signed, httpOnly cookie or Bearer header) | The authenticated `Merchant` user |

Public (unauthenticated) routes: login, register, password reset, `/docs`, `/actuator/health`, and the
**Nomba webhook receiver** (`POST /v1/webhooks/nomba` — HMMA-authenticated, see below).

### Frontend Architecture

The dashboard is a separate Next.js 16 app (`web/`) deployed on Vercel. It calls the Cyrus API from
the browser via JWT (set as an httpOnly cookie by the login endpoint). Public developer docs pages
read no auth — they're static content explaining the API.

---

## Authentication

### 1. Merchant Dashboard Login (JWT + Refresh Token)

Merchants register with email + password. On login:

- **Password verification** via Spring Security's `DaoAuthenticationProvider` + **BCrypt** (`BCryptPasswordEncoder`)
- On success, a **token pair** is generated:
  - **Access token**: RSA-signed JWT (15-minute lifetime), contains merchant email + `ROLE_MERCHANT` scope
  - **Refresh token**: 30-day lifetime, SHA-256 hashed before DB storage, used for rotation only
- Both tokens are delivered as **httpOnly cookies** (`cyrus_token` + `cyrus_refresh`)
- The refresh token is scoped to `/v1/auth` and only sent to the refresh endpoint

**Token rotation:**
- Frontend proactively refreshes every 14 minutes via `POST /v1/auth/refresh`
- Backend revokes the old refresh token and issues a new pair (rotation)
- On 401, frontend automatically attempts refresh before redirecting to login
- Cross-tab coordination via `localStorage`-based locking prevents concurrent rotations

**Logout:** Revokes the refresh token in DB, clears both cookies.

**Why httpOnly + refresh tokens?** Prevents XSS-based token theft (tokens never touch JavaScript).
Short-lived access tokens limit exposure if compromised; refresh tokens enable long-lived sessions
without storing sensitive data client-side.

### 2. Server-to-Server (API Key)

Developers authenticate programmatic requests with an API key:

- **Format:** `cyrus_` + 32 cryptographically random bytes (URL-safe Base64) = 56-char string
- **Header:** `Authorization: Bearer cyrus_<key>`
- **Storage (at rest):** The raw key is **shown exactly once** at creation, then **only its SHA-256
  hash is persisted**. The raw key is never stored, logged, or recoverable.
- **Validation:** Incoming key is SHA-256 hashed and matched against the stored hash. Constant-time
  comparison via `MessageDigest.isEqual` — no timing side-channel.
- **Revocation:** Keys can be revoked (status → `REVOKED`), preventing further use.
- **Expiry:** Optional `expiresAt` per key.

Key lifecycle is managed through the dashboard settings page — no developer-facing API for key
management (it's a dashboard operation, not a programmatic one).

### 3. Separation of Concerns

| Concern | Mechanism |
|---|---|
| Password storage | BCrypt hash, never plaintext |
| API key storage | SHA-256 hash, never plaintext |
| Access token | 15-min JWT, httpOnly cookie (`cyrus_token`) |
| Refresh token | 30-day token, SHA-256 hashed in DB, httpOnly cookie (`cyrus_refresh`) |
| Token transmission | httpOnly cookies (dashboard) or Bearer header (API) |
| Encryption key | `APP_ENCRYPTION_KEY` — AES-256-GCM, Base64-decoded to 16/24/32 bytes, validated at startup |
| JWT signing | RSA-256 (asymmetric) — public key for verification, private key for signing |

---

## Webhooks

### Inbound Webhooks (Nomba → Cyrus)

When a payment lands in a Cyrus virtual account (or a payout status changes), Nomba POSTs to
`POST /v1/webhooks/nomba`.

**Signature verification:**

1. Cyrus extracts signed fields from the JSON payload: `event_type`, `requestId`, `userId`,
   `walletId`, `transactionId`, `type`, `time`, `responseCode`
2. These are concatenated into a colon-delimited canonical string, joined with the
   `nomba-timestamp` header value
3. The string is HMAC-SHA256-signed using `NOMBA_WEBHOOK_SECRET` (shared secret, only Cyrus
   and Nomba know it)
4. The computed HMAC is compared to the `nomba-signature` header using **constant-time comparison**
   (`MessageDigest.isEqual`) — prevents timing attacks
5. Missing/null fields are normalized to empty string to handle payload variations

**Webhook processing pipeline:**

```
Nomba POST → HMAC verify → Normalize payload (naira→kobo) → Ingest (@Transactional)
  ├─ Idempotency check (by requestId) → duplicates silently ignored (return 2xx)
  ├─ Reversal → flip original transaction to REVERSED, refund wallet if credited
  ├─ Non-VA-credit events (POS purchases, etc.) → recorded as IGNORED (no transaction)
  ├─ Unknown virtual account → recorded as orphan (UNKNOWN_VIRTUAL_ACCOUNT)
  ├─ Known but non-ACTIVE VA → recorded as orphan (INACTIVE_CUSTOMER)
  └─ Valid VA credit → create PENDING transaction → schedule reconciliation
       ↓
  Reconciliation (async, then sweep fallback)
       ├─ Requery Nomba as source of truth
       ├─ MATCHED: credit wallet via double-entry ledger, emit payment.succeeded
       └─ DISCREPANCY: still credit wallet but flag for review
```

**Why always return 2xx?** The webhook endpoint always returns a success to Nomba (even for
signature mismatches, duplicates, orphans, non-credit events). Only transient DB/network failures
return non-2xx. This prevents Nomba's 5-retry storm on expected conditions.

### Outbound Webhooks (Cyrus → Merchant)

When a transaction reaches a terminal state, Cyrus delivers a webhook to the merchant's registered
endpoint.

**Signature format (Stripe-style):**

```
X-Cyrus-Event: payment.succeeded
X-Cyrus-Delivery: <uuid>
X-Cyrus-Timestamp: <epoch-millis>
X-Cyrus-Signature: sha256=<hex>
```

The signature covers `timestamp + "." + payload` using **HMAC-SHA256** with the merchant's
`whsec_` secret (generated per merchant, stored AES-256-GCM encrypted at rest).

**Why include timestamp in the signed content?** It binds the timestamp into the HMAC, so a
merchant can verify both the authenticity and freshness of the webhook. A replay window check
(`now - X-Cyrus-Timestamp < tolerance`) prevents replay attacks.

**Delivery guarantees:**

- **Idempotency key:** `X-Cyrus-Delivery` (UUID) — merchants should deduplicate on
  `transactionId` + event type, not delivery ID
- **Retry policy:** Exponential backoff up to `app.webhook.max-attempts`:
  - 2xx → acknowledged, no retry
  - 5xx, 429, timeouts, DNS/connection errors → retry with backoff
  - Other 4xx → permanent failure (no retry)
- **Persistence:** Undelivered webhooks survive restarts (JobRunr with PostgreSQL durability)
- **SSRF guard:** Registered webhook URLs are validated against loopback, link-local, private,
  and multicast IP ranges — Cyrus never sends webhooks to internal network addresses

---

## Data Handling

### Money Model

All monetary amounts are stored and computed as **BigDecimal kobo at scale 4** (`numeric(38,4)`,
minor units with sub-kobo precision). ₦1 = 100 kobo.

- **Never store naira or floating-point money** in the database
- Nomba reports amounts as naira decimal strings (e.g. `"281946.0"`) — converted to kobo at the
  provider boundary via `NombaCurrencyUtil.nairaToKobo()` (×100, normalized to scale 4); sub-kobo
  remainders are preserved, not rounded away
- Fractional kobo flows through fee math, ledger entries and wallet balances; rounding to whole
  kobo happens only at the payout settlement edge (`PayoutService.koboToNaira`), where Nomba's
  transfer API requires whole kobo
- Money is never compared with `equals()` (BigDecimal equality is scale-sensitive) — always
  `compareTo`/`signum`
- Display conversion (kobo → naira) happens **only at the frontend display edge** (`naira()` in
  the Next.js app)
- All arithmetic is exact decimal — no floating-point drift

### Wallet & Ledger (Double-Entry)

Each merchant has a wallet (one per environment). The wallet balance is derived from an **append-only,
double-entry LedgerEntry trail**:

| Operation | Debit | Credit |
|---|---|---|
| Inbound payment | — | Merchant wallet |
| Nomba fee | Merchant wallet | — |
| Platform fee | Merchant wallet | — |
| Payout (outbound) | Merchant wallet | — |
| Payout refund (on failure) | — | Merchant wallet |
| Reversal (payment reversed) | Merchant wallet | — |

Every mutation:

1. Acquires a **pessimistic write lock** (`SELECT ... FOR UPDATE`) on the wallet row — concurrent
   credits/debits serialize at the database level
2. Writes an immutable `LedgerEntry` record (type, amount, description, reference transaction)
3. The wallet balance is a running total — always recoverable by replaying all entries

Guard: `InsufficientFundsException` is thrown if a debit would make the balance negative.

### Encryption at Rest

Sensitive data is encrypted with **AES-256-GCM**:

| Data | Encryption | Key |
|---|---|---|
| Merchant webhook signing secret (`whsec_...`) | AES-256-GCM (12-byte random IV, 128-bit auth tag) | `APP_ENCRYPTION_KEY` (Base64, 16/24/32 bytes) |
| (Nomba credentials are no longer stored per-merchant — they are platform env config) | — | — |

Format: `Base64([12-byte IV][ciphertext + 128-bit tag])` — IV is prepended, not stored separately.

### Database Schema

- **Flyway-managed migrations** (`ddl-auto: validate`) — schema changes are explicit SQL files,
  not auto-generated from entities
- All tables use UUID primary keys, with `created_at`/`updated_at` auditing via JPA entity listeners
- Money columns are `NUMERIC(38,4)` (kobo with sub-kobo precision), never `FLOAT` or `DOUBLE`
- Indexes: unique constraints on `(merchant_id, reference)` for customers, `(merchant_id, environment)`
  for wallets, `key_hash` for API keys, `request_id` for idempotency, `(merchant_id, status)` for
  webhook event queries

### Error Contract

All API responses use the `CyrusApiResponse<T>` envelope:

```json
{
  "status": false,
  "code": "INVALID_INPUT",
  "description": "Validation failed",
  "message": "firstName: must not be blank",
  "data": { "fieldErrors": { "firstName": "must not be blank" } },
  "timestamp": "2026-07-10T12:00:00Z"
}
```

- Client errors (4xx) → warn-level log, client-safe message, no stack trace
- Server errors (5xx) → error-level log with short `traceId` echoed to caller for support
  correlation; no raw provider payloads or stack traces leaked
- 404 for "not found" and "not yours" are identical — no information leaking about existence
  of other merchants' resources

### Platform Profit Ledger (Proposed)

To give the super-admin reliable visibility into actual platform profitability, we propose adding a
**lightweight, append-only profit ledger** tied to the existing `Transaction` and payout flows. This
is not a replacement for merchant wallets; it is a separate ledger that tracks the aggregate
movement of funds through Cyrus's Nomba account.

**Current state:**
- Merchant wallets reflect individual credits/debits but do not answer “how much profit has Cyrus
  made?”
- `NombaBalanceClient` can fetch the provider snapshot, but there's no durable ledger to reconcile
  against it.
- Super-admin lacks a single view of provider balance, merchant liabilities, pending payouts, and
  accrued platform fees.
- Fee configuration is now a single-row `FeeConfig` table (inflow percent with min/max caps,
  payout flat fee) loaded into `FeeProperties` at startup and refreshed on admin update.

**Proposed model (profit ledger):**
- Add a `platform_profit_entries` table: `id`, `transaction_id` (nullable), `payout_id` (nullable),
  `entry_type`, `amount_kobo` (signed), `description`, `created_at`.
- Entry types: `PROFIT_INFLOW` (confirmed payment received), `PROFIT_OUTFLOW` (payout sent/refunded),
  `PROFIT_FEE_ACCRUAL` (platform fee earned, derived from merchant fee minus provider fee),
  `PROFIT_ADJUSTMENT` (manual correction).
- The profit ledger's running total is derived (SUM of entries); no mutable balance column needed
  (though a materialized view may be added for performance).
- Every inflow/outflow writes to both the merchant wallet and the profit ledger in the **same
  transaction**, ensuring consistency.
- The `PROFIT_FEE_ACCRUAL` entry uses the margin computed by `FeeCalculator` (merchant fee − Nomba
  fee), so the profit ledger reflects actual Cyrus earnings, not gross merchant fees.

**Reconciliation with provider:**
- Scheduled sweep: sum profit entries → expected provider balance.
- Call `NombaBalanceClient` for actual provider balance.
- Persist a snapshot (`expected`, `actual`, `delta`, `outcome`).
- If delta outside tolerance → flag discrepancy for review; auto-adjust only for known, rule-based
  differences (e.g., rounding, undisputed fee variance).
- The sweep uses the current `FeeProperties` to validate that fee accruals are consistent with
  the active configuration; drift triggers a review rather than an automatic correction.

**Super-admin dashboard:**
- `GET /v1/platform/profit-summary` (JWT, super-admin only) returns:
  - `provider_balance_kobo` (last synced)
  - `merchant_liabilities_kobo` (sum of all merchant wallet balances)
  - `pending_payouts_kobo` (sum of in-flight payouts)
  - `platform_profit_kobo` (derived from profit ledger)
  - `last_sync_at`, `reconciliation_status`
  - `fee_config_snapshot` (current inflow percent, min/max, payout fee) for transparency

**Why this fits:**
- Uses existing transaction/payout IDs as anchors, so every profit entry is traceable.
- Same-atomicity with merchant wallets keeps both ledgers consistent.
- Lightweight (single small table, no new domain service beyond posting + sweep).
- Aligns with the updated fee model (single-row config, min/max caps) by deriving profit entries
  from the margin, not the gross fee.
- Extensible: if another provider is added later, we just add provider-balance legs; profit model
  stays the same.

---

### BigDecimal Migration Review & Bugs Found

The codebase has been migrated from `BigInteger` (integer kobo) to `BigDecimal` (kobo at scale 4)
across all entities, services, repositories, and API surfaces. Schema migration `V12__money_to_numeric_38_4.sql`
widened all money columns from `numeric(38)` to `numeric(38,4)`. A new `MoneyUtil` utility provides
canonical normalization (`HALF_EVEN`, scale 4) and a `ZERO_KOBO` constant.

**Bugs found during review:**

1. **`WalletBalanceResponse` stale javadoc** (`models/responses/WalletBalanceResponse.java:5`):
   Says "integer kobo" but the value is now `BigDecimal` at scale 4. Documentation only — not a
   runtime bug, but misleading for API consumers.

2. **`LedgerService.post` — `newBalance` not normalized** (`services/LedgerService.java:51`):
   `wallet.getAvailableBalance().add(delta)` — `delta` is normalized, but the loaded balance
   *could* theoretically arrive at a different scale from the JDBC driver. The sum is set on the
   wallet without a defensive `MoneyUtil.normalize()`. In practice PostgreSQL `numeric(38,4)` always
   returns scale 4, and all writes go through this path, so this is a **latent risk, not a live
   bug** — but should be hardened.

3. **`PlatformAdminService.buildLedgerIntegrity` — `BigDecimal.ZERO` instead of
   `MoneyUtil.ZERO_KOBO`** (`services/PlatformAdminService.java:135`): The default value for a
   missing wallet in the ledger map is `BigDecimal.ZERO` (scale 0). `compareTo` ignores scale, so
   the comparison is correct, but it's inconsistent with the rest of the codebase which uses
   `MoneyUtil.ZERO_KOBO`.

4. **`TransactionIngestionService.handleReversal` — `BigDecimal.ZERO` fallback**
   (`services/TransactionIngestionService.java:244-245`): The null-guard fallback for `tx.getFee()`
   and `tx.getPlatformFeeKobo()` uses `BigDecimal.ZERO` (scale 0) instead of `MoneyUtil.ZERO_KOBO`.
   Functionally correct (the subsequent `ledgerService.debit` normalizes), but inconsistent.

5. **Merchant webhook payload contract change** (`services/MerchantWebhookService.java:108-113`):
   JSON money fields (`amountKobo`, `feeKobo`) now serialize as scale-4 BigDecimal values (e.g.
   `1500.0150`) instead of plain integers. Merchants parsing these as integers will silently
   truncate fractional kobo. This is a **breaking API contract change** that should be communicated
   in release notes.

6. **`FeeProperties` defaults at scale 0** (`config/properties/FeeProperties.java:35-39`):
   `inflowMinKobo`, `inflowMaxKobo`, `payoutFlatFeeKobo` are initialized as `new BigDecimal("1500")`
   (scale 0). They're normalized downstream before use, so this is safe — but ideally should be
   created at scale 4 or normalized in `FeeConfigService.applyToProperties()` for consistency.

**Plan adjustments for the profit ledger (BigDecimal):**
- All `platform_profit_entries` columns must use `@Column(precision = 38, scale = 4)`.
- The profit ledger's running total is a `BigDecimal` sum, always normalized via `MoneyUtil.normalize()`.
- The scheduled reconciliation sweep compares `BigDecimal` values using `compareTo`, never `equals`.
- The profit summary endpoint returns `BigDecimal` amounts at scale 4 — same contract as the rest
  of the API.
- The migration for the profit ledger should use `numeric(38,4)` for all money columns, matching
  `V12__money_to_numeric_38_4.sql`.

---

### Misattributed Payment Recovery

When a payment arrives for an unresolvable or misattributed virtual account:

1. **Known VA, wrong customer state** (suspended/closed): visible to the merchant dashboard as
   a reattributable orphan payment
2. **Unknown VA** (not provisioned by any merchant): visible only to super-admins through
   a dedicated orphan recovery surface (`/v1/platform/orphans`)

Both paths let an operator select the correct customer and re-run the reconciliation pipeline —
the payment is attributed retroactively without the merchant or customer needing to do anything.

---

## Deployment & Infrastructure

| Component | Platform | Notes |
|---|---|---|
| **API (Spring Boot)** | GCP Cloud Run (serverless container) | `api.trycyrus.app` |
| **Frontend (Next.js)** | Vercel | `trycyrus.app` |
| **Database** | Cloud SQL for PostgreSQL | Managed, automated backups |
| **Background jobs** | JobRunr (PostgreSQL-backed) | Outbound webhook delivery retries only |
| **Credentials** | GCP Secret Manager | All secrets (DB, Nomba, RSA, encryption key) |
| **CI/CD** | GitHub Actions | Auto-deploy on push to `main` |

### Environment Separation

- **Test/LIVE** is determined by the Nomba credentials used (sandbox vs. live client ID/secret
  from env vars), not by separate deployments or API keys
- Each merchant has one API key — the environment is a platform config, not a per-key concern
- Wallets are per-merchant per-environment (a single merchant has a test wallet and a live wallet)

---

## Summary

| Area | Approach |
|---|---|
| **Password auth** | BCrypt, JWT in httpOnly cookie |
| **API auth** | Bearer token, SHA-256 hashed at rest, constant-time validation |
| **Inbound webhook security** | HMAC-SHA256 signature, canonical string, constant-time comparison |
| **Outbound webhook security** | Stripe-style HMAC (`timestamp.payload`), merchant `whsec_` secret encrypted at rest |
| **Data encryption at rest** | AES-256-GCM for webhook secrets |
| **Money** | BigDecimal kobo (scale 4) everywhere, never floating-point |
| **Wallet** | Double-entry append-only ledger, pessimistic locking |
| **Error handling** | Structured envelope, no stack/data leaks to client |
| **Schema changes** | Flyway migrations, no auto-DDL |
| **SSRF protection** | Pre-flight URL validation against private/reserved IP ranges |
