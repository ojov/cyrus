# Database Relationships

> Entity-relationship reference for the Cyrus PostgreSQL schema. All tables are managed by Flyway (`ddl-auto: validate`); any schema change is a new `V{n}__description.sql` migration, never an entity-only edit.

---

## Table of Contents

- [Entity-Relationship Diagram](#entity-relationship-diagram)
- [Relationship Details](#relationship-details)
- [Tables](#tables)
- [Money Model](#money-model)
- [Data Flow](#data-flow)

---

## Entity-Relationship Diagram

```mermaid
erDiagram
    merchants ||--o| wallets : "has one"
    merchants ||--o{ merchant_customers : "has many"
    merchants ||--o{ api_keys : "has many"
    merchants ||--o{ tokens : "has many"
    merchants ||--o{ refresh_tokens : "has many"
    merchants ||--o{ beneficiaries : "has many"
    merchants ||--o{ nomba_payment_events : "receives"
    merchants ||--o{ transactions : "owns"
    merchants ||--o{ payouts : "initiates"
    merchants ||--o{ merchant_webhook_events : "emits"

    merchant_customers ||--|| virtual_accounts : "has one"
    merchant_customers ||--o{ transactions : "linked to"

    virtual_accounts ||--o{ transactions : "receives payments into"
    virtual_accounts ||--o{ nomba_payment_events : "credited by"

    transactions ||--o| nomba_payment_events : "sourced from"
    transactions ||--o| payouts : "paired with"
    transactions ||--o{ ledger_entries : "posts to"
    transactions ||--o| merchant_webhook_events : "triggers"
    transactions ||--o{ platform_profit_entries : "tracked by"

    payouts ||--|| beneficiaries : "settles to"
    payouts ||--o{ platform_profit_entries : "tracked by"

    wallets ||--o{ ledger_entries : "balanced by"

    merchant_webhook_events ||--o{ webhook_deliveries : "attempted via"

    merchants {
        UUID id PK
        string business_name UK
        string business_email UK
        string business_type
        string phone
        string password_hash
        string status
        string role
        int virtual_account_limit
        string url "embedded WebhookConfig.url"
        string encrypted_secret "embedded WebhookConfig.encryptedSecret"
    }

    merchant_customers {
        UUID id PK
        UUID merchant_id FK
        string external_customer_id
        string first_name
        string last_name
        string email
        string phone_number
        string status
        string kyc_tier
        text metadata
    }

    virtual_accounts {
        UUID id PK
        UUID merchant_customer_id FK "UNIQUE"
        string account_number UK
        string account_name
        string bank_name
        string provider_reference
        string currency
        string status
    }

    transactions {
        UUID id PK
        UUID merchant_id FK
        UUID customer_id FK "nullable"
        UUID virtual_account_id FK "nullable"
        UUID payment_event_id FK "1:1, nullable"
        string type
        string reference UK
        string provider_transaction_id UK
        string request_id "nullable, the inbound webhook requestId"
        string session_id
        numeric amount "kobo, scale 4"
        numeric fee
        numeric platform_fee_kobo
        numeric merchant_fee_kobo
        string currency
        string status
        string match_status
        string match_status_details
        int reconciliation_attempts
        timestamp last_reconciled_at
        timestamp received_at
        string payer_name
        string payer_account_number
        string payer_bank
        text narration
        text raw_payload
        bigint version "optimistic lock"
    }

    wallets {
        UUID id PK
        UUID merchant_id FK "UNIQUE"
        numeric available_balance "kobo, scale 4"
        bigint version
    }

    ledger_entries {
        UUID id PK
        UUID transaction_id FK
        UUID wallet_id FK
        numeric amount "signed kobo"
        string type
        string description
    }

    nomba_payment_events {
        UUID id PK
        string request_id UK
        string event_type
        UUID merchant_id FK "nullable"
        UUID virtual_account_id FK "nullable"
        string transaction_id
        string session_id
        string account_number
        string customer_reference
        numeric amount
        numeric fee
        string status
        string failure_reason
        text raw_payload
        string signature
    }

    beneficiaries {
        UUID id PK
        UUID merchant_id FK
        string nickname
        string account_name
        string account_number
        string bank_code
        string bank_name
        string provider_beneficiary_id
    }

    payouts {
        UUID id PK
        UUID merchant_id FK
        UUID beneficiary_id FK
        UUID transaction_id FK "1:1"
        string reference UK
        string provider_reference
        numeric amount "kobo, scale 4"
        numeric fee
        text narration
        string status
        string failure_reason
    }

    api_keys {
        UUID id PK
        UUID merchant_id FK
        string key_hash UK
        string prefix
        string status
        timestamp last_used_at
        timestamp expires_at
        timestamp revoked_at
    }

    tokens {
        UUID id PK
        UUID merchant_id FK
        string token UK
        string type
        timestamp expires_at
        boolean used
    }

    refresh_tokens {
        UUID id PK
        UUID merchant_id FK
        string token UK
        timestamp expires_at
        boolean revoked
        string user_agent
        string ip_address
    }

    merchant_webhook_events {
        UUID id PK
        UUID merchant_id FK
        UUID transaction_id FK "1:1"
        string event_type
        string webhook_url
        text payload
        string status
        int attempts
        int last_response_code
        string last_error
        timestamp next_retry_at
        timestamp delivered_at
    }

    webhook_deliveries {
        UUID id PK
        UUID webhook_event_id FK
        int attempt_number
        int response_status
        text response_body
        string failure_reason
    }

    fee_config {
        UUID id PK
        numeric inflow_percent
        numeric inflow_min_kobo
        numeric inflow_max_kobo
        numeric payout_flat_fee_kobo
        bigint version
    }

    platform_profit_entries {
        UUID id PK
        UUID transaction_id FK "nullable"
        UUID payout_id FK "nullable"
        string entry_type
        numeric amount_kobo "signed kobo"
        string description
    }
```

---

## Relationship Details

### Merchant → Customers → Virtual Accounts

```mermaid
flowchart LR
    M[Merchant] -->|"1 : N"| MC[MerchantCustomer]
    MC -->|"1 : 1"| VA[VirtualAccount]
```

- Each merchant has many customers.
- Each customer has exactly one dedicated virtual account (1:1, `MerchantCustomer` is the owning side).
- The `external_customer_id` on `MerchantCustomer` doubles as the Nomba `accountRef` — the value echoed on every incoming webhook as `aliasAccountReference`.
- Customer status (`ACTIVE` / `SUSPENDED` / `CLOSED`) cascades to the virtual account's Nomba-side status.

### Transaction Links

```mermaid
flowchart LR
    T[Transaction] -->|"N : 1"| M[Merchant]
    T -->|"N : 1, nullable"| MC[MerchantCustomer]
    T -->|"N : 1, nullable"| VA[VirtualAccount]
    T -->|"1 : 1, nullable"| NPE[NombaPaymentEvent]
```

- `Transaction.customer` and `Transaction.virtualAccount` are nullable — orphan payments (unresolved VA numbers) are stored without a customer link until reattributed.
- `Transaction.paymentEvent` is nullable for internal-origin transactions (e.g. payouts created from `PayoutService`, not from inbound webhooks).

### Wallet & Ledger (Double-Entry)

```mermaid
flowchart LR
    M[Merchant] -->|"1 : 1"| W[Wallet]
    W -->|"1 : N"| LE[LedgerEntry]
    LE -->|"N : 1"| T[Transaction]
```

- Each merchant has exactly one wallet.
- `Wallet.availableBalance` is a materialized running total of all `LedgerEntry.amount` values for that wallet.
- Ledger entries are append-only and signed: positive = credit, negative = debit.
- The wallet uses optimistic locking (`@Version`) to prevent concurrent balance corruption.

### Payout Flow

```mermaid
flowchart LR
    M[Merchant] -->|"1 : N"| P[Payout]
    B[Beneficiary] -->|"1 : N"| P
    P -->|"1 : 1"| T[Transaction]
    P -.->|"optional"| PPE[PlatformProfitEntry]
```

- A payout references the beneficiary (destination bank account) and the paired `Transaction` of type `PAYOUT`.
- The beneficiary's `account_number` + `bank_code` are the settlement key — verified against Nomba before payout initiation.

### Webhook Outbox (Outbound)

```mermaid
flowchart LR
    MWE[MerchantWebhookEvent] -->|"1 : N"| WD[WebhookDelivery]
    MWE -->|"1 : 1"| T[Transaction]
    M[Merchant] -->|"1 : N"| MWE
```

- `MerchantWebhookEvent` is an outbox row: written in the same transaction as the status change, then picked up by `MerchantWebhookDispatcher` via JobRunr.
- `WebhookDelivery` records each individual POST attempt (append-only audit trail).
- The unique constraint on `(transaction_id, event_type)` makes outbox creation idempotent.

### Platform Profit Ledger

```mermaid
flowchart LR
    PPE[PlatformProfitEntry] -->|"N : 1, nullable"| T[Transaction]
    PPE -->|"N : 1, nullable"| P[Payout]
```

- Every entry is anchored to either a `Transaction` or a `Payout` (or neither for manual adjustments).
- Written in the **same DB transaction** as the corresponding merchant wallet posting, ensuring both ledgers stay consistent.
- Running total is derived via `SUM(amount_kobo)` — there is no mutable balance column.

### Fee Configuration

- `fee_config` is a single-row table holding platform-wide fee parameters used by `FeeCalculator` and `FeeProperties`.
- Updated via super-admin API; loaded into memory at startup and refreshed on every update.
- Uses optimistic locking (`@Version`) to prevent concurrent overwrites.

---

## Tables

All tables inherit from `BaseEntity`, which provides:

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key, auto-generated |
| `created_at` | `timestamptz` | Set once at insert via JPA auditing |
| `updated_at` | `timestamptz` | Updated on every modification |

### Core Domain Tables

| Table | Description | Key Constraints |
|---|---|---|
| `merchants` | Developer/business accounts | `business_email` UNIQUE |
| `merchant_customers` | Customers belonging to a merchant | UNIQUE `(merchant_id, external_customer_id)` |
| `virtual_accounts` | Nomba VAs, 1:1 with each customer | `account_number` UNIQUE, `merchant_customer_id` UNIQUE |
| `transactions` | Every money movement (payments, payouts, reversals) | `reference` UNIQUE, `provider_transaction_id` UNIQUE |
| `wallets` | Per-merchant balance | `merchant_id` UNIQUE |
| `ledger_entries` | Append-only double-entry audit trail | indexed on `transaction_id`, `wallet_id`, `type` |

### Provider Integration Tables

| Table | Description | Key Constraints |
|---|---|---|
| `nomba_payment_events` | Deduplicated inbound webhook records | `request_id` UNIQUE |
| `beneficiaries` | Bank accounts for merchant payouts | indexed on `merchant_id`, `(merchant_id, account_number)` |
| `payouts` | Outbound withdrawal records | `reference` UNIQUE, indexed on `status` |

### Platform Tables

| Table | Description | Key Constraints |
|---|---|---|
| `fee_config` | Single-row global fee configuration | N/A (one row, `@Version` for optimistic locking) |
| `platform_profit_entries` | Append-only platform profit ledger | indexed on `transaction_id`, `payout_id`, `entry_type`, `created_at` |

### Auth & Webhook Tables

| Table | Description | Key Constraints |
|---|---|---|
| `api_keys` | SHA-256 hashed API keys | `key_hash` UNIQUE, indexed for lookup |
| `tokens` | Single-use verification/reset tokens | `token` UNIQUE |
| `refresh_tokens` | Dashboard session refresh tokens | `token` UNIQUE |
| `merchant_webhook_events` | Outbox for outbound merchant webhooks | UNIQUE `(transaction_id, event_type)` |
| `webhook_deliveries` | Per-attempt delivery audit trail | indexed on `webhook_event_id` |

---

## Money Model

All monetary values use **`BigDecimal` kobo at scale 4** (`numeric(38,4)` in PostgreSQL).

| Rule | Detail |
|---|---|
| **Storage unit** | Kobo, never naira. ₦1 = 100 kobo. |
| **Precision** | Scale 4 — sub-kobo precision is preserved through fee math and ledger postings. |
| **Rounding** | Only at payout settlement (`PayoutService.koboToNaira`), where Nomba's transfer API requires whole kobo. |
| **Comparison** | Never use `BigDecimal.equals()` (scale-sensitive). Always use `compareTo()` or `signum()`. |
| **JSON output** | Plain decimal notation (`spring.jackson.write.write-bigdecimal-as-plain: true`). |
| **Provider boundary** | Nomba sends naira as a string (`"281946.0"`). Converted via `NombaCurrencyUtil.nairaToKobo()` (×100, normalize to scale 4). |

### Fee Breakdown on a Payment

```mermaid
flowchart TD
    A["grossAmount (payer sends)"] --> B["nombaFee (Nomba's internal fee)"]
    A --> C["merchantFeeKobo (merchant pays this to Cyrus)"]
    A --> D["netCreditedToWallet"]

    C --> E["inflowPercent × grossAmount"]
    E --> F["clamped to [inflowMinKobo, inflowMaxKobo]"]

    C --> G["merchantFeeKobo"]
    B --> G
    G --> H["platformFeeKobo (Cyrus's margin)"]

    style D fill:#d4edda,stroke:#28a745
    style H fill:#fff3cd,stroke:#ffc107
```

---

## Data Flow

### Inbound Payment (Webhook)

```mermaid
sequenceDiagram
    participant N as Nomba
    participant W as WebhookService
    participant A as Adapter
    participant I as IngestionService
    participant DB as Database
    participant R as ReconciliationService

    N->>W: POST /v1/webhooks/nomba
    W->>W: verifySignature()
    W->>A: normalize() — naira → kobo
    A->>I: ingest()

    I->>DB: Idempotency check (requestId)
    alt Duplicate
        I-->>N: 2xx (silently ignored)
    else New event
        I->>DB: Resolve VA by account_number
        alt Unknown VA
            I->>DB: Store as IGNORED (UNKNOWN_VIRTUAL_ACCOUNT)
        else Known but inactive VA
            I->>DB: Store as IGNORED (INACTIVE_CUSTOMER)
        else Valid VA credit
            I->>DB: Create NombaPaymentEvent (PROCESSED)
            I->>DB: Create Transaction (PENDING)
            I-->>N: 2xx
            Note over I,R: afterCommit — async
            I->>R: scheduleReconciliation()
            R->>N: Requery by sessionId
            R->>DB: Set matchStatus (MATCHED / DISCREPANCY)
            R->>DB: Credit wallet (LedgerEntry)
            R->>DB: Write platform profit entry
            R->>DB: Create outbound webhook (outbox row)
        end
    end
```

### Outbound Payout

```mermaid
sequenceDiagram
    participant M as Merchant
    participant S as PayoutService
    participant DB as Database
    participant N as Nomba
    participant W as WebhookService

    M->>S: POST /v1/merchants/me/payouts

    rect rgb(230, 245, 255)
        Note over S,DB: Phase 1 — Reserve funds
        S->>DB: Debit wallet (LedgerEntry)
        S->>DB: Create Payout (PENDING)
        S->>DB: Create Transaction (PENDING)
    end

    rect rgb(255, 245, 230)
        Note over S,N: Phase 2 — Nomba transfer (no tx open)
        S->>N: transfer()
        N-->>S: Response
    end

    rect rgb(230, 255, 230)
        Note over S,DB: Phase 3 — Finalize
        alt Success
            S->>DB: Payout + Transaction → SUCCESS
            S->>DB: Set fee from requery
        else Failure
            S->>DB: Refund wallet (REVERSAL LedgerEntry)
            S->>DB: Payout → FAILED
        end
    end

    Note over W: Late/async webhook
    N->>W: payout_success / payout_failed webhook
    W->>S: applyWebhook()
    S->>DB: Find Payout (pessimistic lock)
    alt Already terminal
        S-->>W: No-op (idempotent)
    else Still processing
        alt Success
            S->>DB: Finalize payout
        else Failure
            S->>DB: Refund wallet
        end
    end
```

### Wallet Ledger Posting Rules

| Event | LedgerEntry Type | Direction | Amount |
|---|---|---|---|
| Confirmed inbound payment | `CREDIT` | Credit wallet | `gross - nombaFee - merchantFee` |
| Payout initiated | `DEBIT` | Debit wallet | `payout amount + payout fee` |
| Payout failure/refund | `REVERSAL` | Credit wallet | `payout amount + payout fee` (reversed) |
| Payment reversal | `REVERSAL` | Debit wallet | `net credited amount` (clawback) |
