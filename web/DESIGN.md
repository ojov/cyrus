# Cyrus — Design Doc & PRD Additions

> **Purpose.** Two things in one file: (1) **paste-ready PRD additions** (Part A) you can lift straight
> into the Google Doc, and (2) our **internal design spec** (Parts B & C) for the frontend redesign.
> The interactive visual prototype (the "Figma") is linked at the end of Part C.
>
> **Framing.** The Nomba challenge is judged on **reconciliation accuracy, identity & naming-model
> quality, edge-case handling (renames / closure / KYC tier changes), and — our priority — a clean
> developer API for downstream integration.** Every addition below is tied back to one of those.
>
> **Companion Google Doc** (Part A, formatted for the PRD):
> https://docs.google.com/document/d/1iEsJhysW5YA0cwlb_B1uhzjcjzmBKr_Y4C2JIbwRKys/edit
> **Interactive prototype:** https://claude.ai/code/artifact/7331b1f1-5050-4c02-8500-a9cf73d03099

---

## Part A — PRD additions (paste-ready)

### §10 Frontend / UX

Cyrus ships **two distinct surfaces**, for two audiences. This separation is deliberate: developers
integrate through the **API**, not a dashboard; operations teams supervise through the **dashboard**.

| Surface | Audience | Auth | Purpose |
|---|---|---|---|
| **Developer site** (`/`) | Product engineers | none / API key in examples | Landing + **live API reference**: quickstart, auth, Customers, Virtual Accounts, Transactions & statements, Webhooks, Reconciliation, Errors. The developer never sees a raw Nomba payload. |
| **Admin/Ops dashboard** (`/dashboard`) | Business admin / ops | JWT (dashboard login) | Supervise, don't build: manage API keys, watch customers & virtual accounts, read statements, and — the operational core — **monitor reconciliation and resolve misdirected payments**. |

**Every dashboard page maps to a Must-Include and a judged criterion:**

| Dashboard page | Must-Include it serves | Judged criterion it demonstrates |
|---|---|---|
| Overview | — | reconciliation health at a glance |
| Customers + Customer detail | Account provisioning flow | identity & naming-model quality |
| Customer statement | **Customer-level statement & reporting** | developer API + reporting quality |
| Transactions | Inbound transfer reconciliation | reconciliation accuracy |
| **Reconciliation / Exceptions** | Inbound reconciliation + **misdirected payments** | reconciliation accuracy + edge-case handling |
| API Keys | Clean developer API | developer API quality |
| Settings / Go-live | Provider connection | provider abstraction |

### §5.8 Customer lifecycle & edge cases (identity & naming model)

The **customer is the stable identity**; the virtual account is a payment instrument attached to it. The
external id supplied by the developer is **immutable**; everything else is mutable without breaking
attribution, because attribution is keyed on the (immutable) virtual-account number, not the name.

| Event | What changes | What stays stable | Provider action |
|---|---|---|---|
| **Rename** (name/email/phone) | Customer display fields; VA `accountName` | `customerId`, `externalCustomerId`, `accountNumber` | update VA alias name |
| **Suspend** | `VirtualAccountStatus → SUSPENDED` (rejects new credits) | identity + history retained | suspend VA at provider |
| **Close** | `VirtualAccountStatus → CLOSED`; customer archived | history retained for audit | deactivate VA at provider |
| **KYC tier change** | `KycTier LEVEL_1 → LEVEL_2` (limits/features) | identity + account number | re-provision limits if required |

Customer states: `ACTIVE → SUSPENDED → CLOSED` (VA status mirrors). External id collisions per merchant
are rejected (idempotent create returns the existing customer).

> **Implementation note (honest current state):** `VirtualAccountStatus (ACTIVE/SUSPENDED/CLOSED)` and
> the rename fields exist today; **`KycTier` is defined as an enum but is not yet a field on the
> `Customer` entity**, and suspend/close/rename endpoints are not yet exposed. See Part B for the
> proposed API surface that closes these gaps.

### §5.9 Customer statement & reporting

Every customer exposes a **statement** — the chronological, attributed record of inbound transfers to
its virtual account, with running context. This is the reporting primitive downstream teams reuse
(invoice reconciliation, rent ledgers, settlement).

```
GET /v1/customers/{externalCustomerId}/transactions?from=&to=&cursor=
→ { "data": { "customer": { "customerId": "cus_…", "externalCustomerId": "user_123" },
              "transactions": [ { "id": "txn_…", "amount": 5000000, "currency": "NGN",
                                  "payerName": "…", "status": "SUCCESSFUL",
                                  "matchStatus": "MATCHED", "receivedAt": "…" } ],
              "nextCursor": null } }
```

Amounts are integer **kobo** on the wire; the UI renders naira (`amount / 100`).

### §5.10 Misdirected / unattributed payments (a first-class, resolvable flow)

An inbound transfer that can't be attributed to a known virtual account is **not dropped** — it is
recorded as an unattributed event and surfaced for resolution. This is both a Must-Include ("handling of
misdirected payments") and a judged edge case.

- **Detect:** webhook credit whose `aliasAccountNumber` matches no VA → `PaymentEvent(status=IGNORED)`;
  a provider record with no matching internal transaction → `Transaction(matchStatus=ORPHANED)`.
- **Surface:** the dashboard **Exceptions** tab lists every unattributed / mismatched item with amount,
  payer, and provider reference.
- **Resolve:** an operator can **re-attribute** to the correct customer, mark **refund/return**, or
  **dismiss** (with reason). Each resolution is audited.

---

## Part B — Developer-API design review (the judged priority)

The API is what we're most judged on. Below is the current surface vs. the proposed clean surface, plus
the conventions that make it feel like a real product (Stripe/Paystack-grade).

### B1 — Identity & naming model

The PRD speaks in `customerId` (`cus_…`) + `externalCustomerId`; the **code currently keys everything on
`reference`** and exposes the raw UUID. Recommendation — adopt the PRD's model for a cleaner, more
stable public contract:

| Concept | Current (code) | Proposed (clean API) |
|---|---|---|
| Cyrus-owned id | `id` (bare UUID) | **`customerId`** = `cus_` + base32 (stable, opaque, sortable) |
| Developer-owned id | `reference` | **`externalCustomerId`** (immutable, unique per merchant) |
| Name | `firstName` / `lastName` | keep; add derived `name` in responses |
| VA id | `accountNumber` | keep (the attribution key) |
| KYC | `KycTier` enum only | add **`kycTier`** field on `Customer` (default `LEVEL_1`) |
| Resource ids | UUIDs | prefixed: `cus_`, `va_`, `txn_`, `evt_`, `key_` |

Prefixed ids read better in logs/docs and signal type — a concrete lift for "naming-model quality".

### B2 — Endpoint map (exists vs proposed)

| Method & path | Status | Purpose |
|---|---|---|
| `POST /v1/auth/register` · `login` · `verify-email` | ✅ exists | dashboard auth |
| `GET/POST/DELETE /v1/merchants/me/api-keys` | ✅ exists | key management |
| `POST /v1/merchants/me/go-live` | ✅ exists | provider connect (live) |
| `GET /v1/merchants/me/stats` | ✅ exists | dashboard counts |
| `POST /v1/customers` | ✅ exists | create customer → auto-provision VA |
| `GET /v1/customers/{externalCustomerId}` | ✅ exists | fetch customer + VA |
| `GET /v1/customers` (list, paginated) | ➕ proposed | dashboard Customers + developer list |
| `PATCH /v1/customers/{id}` (rename) | ➕ proposed | §5.8 rename |
| `POST /v1/customers/{id}/suspend` · `/close` | ➕ proposed | §5.8 suspend/close (VA lifecycle) |
| `GET /v1/customers/{id}/transactions` | ➕ proposed | §5.9 statement |
| `GET /v1/transactions` (query/filter) | ➕ proposed | Transactions API |
| `GET /v1/reconciliation` + `POST …/{id}/resolve` | ➕ proposed | §5.10 + reconciliation report |
| `GET/PUT /v1/webhooks/config` | ➕ proposed | developer webhook endpoint + secret |
| normalized event `payment.received` | 🔶 schema only | Cyrus → developer emission (not built) |
| `POST /v1/webhooks/nomba` (inbound from Nomba) | ✅ exists | provider ingestion |

### B3 — API conventions (make these explicit in the docs)

- **Envelope:** every response is `CyrusApiResponse<T>` — `{ status, code, description, message, data,
  timestamp }`. Errors add `data.fieldErrors` (validation) or `data.traceId` (5xx) only.
- **Auth:** server-to-server uses `Authorization: Bearer <key>`; keys are prefixed `cyrus_test_…` /
  `cyrus_live_…` — the prefix selects the environment. Dashboard uses a JWT.
- **Idempotency:** accept an `Idempotency-Key` header on `POST /v1/customers` (and future mutating
  calls) so retries never double-provision. Inbound webhooks are already idempotent by `requestId`.
- **Pagination:** cursor-based (`?cursor=&limit=`, response `nextCursor`) on all list endpoints.
- **Money:** integer **kobo** on the wire and at rest; **naira only at the display edge**. `currency` is
  always `NGN` for now but explicit.
- **Versioning + errors:** `/v1` prefix; documented `ResponseCode`s (e.g. `DUPLICATE_MERCHANT`,
  `NOMBA_INTEGRATION_ERROR`, `RESOURCE_NOT_FOUND`) with stable machine-readable `code`s.
- **Minor inconsistency to fix:** `ApiKeyStatus` only defines `ACTIVE` (revocation is tracked via
  `revokedAt`), but the frontend renders a `REVOKED` badge — add a `REVOKED` status or derive it
  consistently so the API and UI agree.

---

## Part C — Frontend design language & IA

### C1 — Two surfaces (information architecture)

The app has two **modes**, switched from the sidebar: **Docs** (public) and **Dashboard** (secured).
Developers live in Docs and never sign in; the ops team signs in to reach the Dashboard.

- **Developer site** (`/`, public — no login): docs nav + content pane. Real content, not `href="#"`
  stubs (the current landing is a facade). IA:
  ```
  Getting Started        (signup → API keys; how to authenticate every request)
  Environments
  API Reference ── Authentication · Virtual Accounts · Payments · Transactions · Webhooks · Errors
  API Keys
  Webhook Testing
  Changelog
  SDKs (future)          (Java/Spring first, then Node, Python …)
  ```
  *Getting Started* walks a dev through signing up with a business email + Nomba **sandbox** credentials,
  creating a customer, and later adding **live** credentials in the dashboard.
  **Auth model (keep these unmixed):** the **API** is authenticated with an **API key** issued at signup
  (`cyrus_test_`/`cyrus_live_`, used on every request); the **dashboard** uses a separate email+password
  login for the ops team only. There is no standalone "Authentication" page — the API-key story lives in
  Getting Started and the *API Reference → Authentication* reference.
- **Dashboard** (`/dashboard`, **login-gated**): choosing "Dashboard" while signed out shows a **sign-in
  screen**; only after login does the ops nav appear → **Overview · Customers · Transactions ·
  Reconciliation · API Keys · Settings**. (Today the live app is only Overview / API Keys / Settings and
  has no gate — the redesign adds the login gate + the three feature surfaces that make the judged
  criteria visible.)

### C2 — Design language

- **Palette:** existing purple (`--primary ≈ #6b46c1`, page `#f5f3ff`, oklch tokens in
  `app/globals.css`). Refine, don't rebrand.
- **Dark mode:** the app ships an unused `.dark` theme + `theme-toggle` — **finish it**, don't delete
  it. Both modes are first-class in the prototype.
- **Components:** shadcn/ui (Card, Badge, Button, Input, Select, Table). Mono font for code/account
  numbers/keys; sans for prose.
- **Tone:** docs-inspired (Nomba/Stripe developer-docs feel) — dense but calm, generous whitespace,
  status conveyed by color-coded badges (`MATCHED`=green, `PARTIAL`=amber, `ORPHANED`=red,
  `SUSPENDED`=muted).

### C3 — Page inventory & required states

| Page | Key content | Empty | Loading | Error |
|---|---|---|---|---|
| Landing/Docs | hero, quickstart curl, live reference panels | — | — | — |
| Overview | stat cards + reconciliation health strip | "no data yet" | skeleton cards | toast |
| Customers | table (name, external id, account no, VA status, tier) | "Create your first customer via the API" | skeleton rows | toast |
| Customer detail | identity + VA + **statement** | "no transactions yet" | skeleton | toast |
| Transactions | filterable table, amounts in naira | "no transfers yet" | skeleton | toast |
| Reconciliation/Exceptions | tabs: All / Unmatched / Orphaned; **Resolve** action | "all reconciled ✓" | skeleton | toast |
| API Keys | create/revoke, reveal-once | "no keys yet" | "Loading…" | toast |
| Settings | go-live form, provider status | — | — | inline |

### C4 — Interactive prototype (the "Figma")

A single self-contained, theme-aware HTML prototype captures all of the above with realistic mock data,
navigable between screens.

**Prototype:** https://claude.ai/code/artifact/7331b1f1-5050-4c02-8500-a9cf73d03099

Screens: Documentation (default) · Overview · Customers · Customer detail · Transactions ·
Reconciliation & exceptions · API keys · Settings. Toggle light/dark from the top-right; the left rail
switches surfaces (Developer ↔ Operations).

Docs are interactive: callable API-Reference pages (Authentication, Virtual Accounts, Transactions)
carry a **"Try it" console** in the right column that mocks the request and **requires a test API key**;
every docs page has a **Prev/Next pager** at the foot for sequential reading. The Authentication
reference covers *using* the key only — account creation lives in the dashboard, not the API docs.
