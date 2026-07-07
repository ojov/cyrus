# AGENTS.md — Cyrus Backend

> Guidance for AI agents working in this repo. `CLAUDE.md` is a symlink to this file.
> The `web/` directory is a **separate Next.js 16 frontend** with its own `AGENTS.md`/`CLAUDE.md` — the rules below apply to the Java backend at the repo root, not to `web/`.
>
> **This is a living document — keep it current as the codebase grows.** Several sections below describe deliberate *current-state* choices that will change as we approach submission (e.g. Flyway will be turned on; more API-key endpoints will be added). When you change one of those realities, update this file in the same commit.

## What Cyrus is

Cyrus is a **customer payment identity infrastructure layer** — not a payment processor and not a wallet. Nomba provides the payment rails; Cyrus sits above it and provides **identity, reliability, and orchestration**:

- Maps a persistent `Customer` identity → a dedicated `VirtualAccount` → incoming `Transaction`s → the owning `Merchant`.
- Provisions and manages virtual-account lifecycle via a provider abstraction (Nomba first).
- Is meant to reliably process payment webhooks (idempotent, deduplicated) and reconcile provider records against internal records.
- Exposes provider-agnostic APIs and normalized webhooks so developers never touch raw Nomba payloads.

Cyrus does **not** hold funds. Money flows Customer → Virtual Account → Merchant's provider account directly.

**Project context:** built for the Nomba hackathon; judged primarily on **reconciliation accuracy** and **developer API quality**. Prioritize correctness in those two areas.

## Tech stack

- **Java 25**, **Spring Boot 4.1.0**, Maven (use the wrapper `./mvnw`).
- Base package: `com.ojo.cyrus`.
- **PostgreSQL** + Spring Data JPA + Hibernate. **Lombok** throughout.
- Spring Security (OAuth2 Resource Server for JWT), springdoc OpenAPI, Thymeleaf, Resend (email).
- **JobRunr 8.7.0** — background job scheduling with PostgreSQL durability; reconciliation jobs run on configurable delay/retry schedule. Dashboard UI available on port 8000 (gated per environment in production).
- Deployed on GCP; production API at `https://api.trycyrus.app` (docs at `/swagger-ui/index.html`).

## Build / run / test

```bash
./mvnw clean package         # compile + package (runs tests)
./mvnw spring-boot:run       # run locally on :8080 (SERVER_PORT overrides)
./mvnw test                  # tests only
docker compose up -d         # local Postgres (see compose.yaml)
```

- Local Postgres defaults (from `application.yaml`): `jdbc:postgresql://localhost:5438/cyrus`, user `cyrus` / `cyrus_password`. Note the **non-standard port 5438**.
- Swagger UI: `http://localhost:8080/docs` (also `/swagger-ui/index.html`); OpenAPI JSON at `/v3/api-docs`.
- `spring-boot-docker-compose` is on the runtime classpath, so a local run can auto-start the DB container.

## Required configuration (env vars)

Set these before running (see `application.yaml` for the full list and defaults):

- `APP_ENCRYPTION_KEY` — base64 AES key; **encrypts Nomba credentials at rest**. Required. Must Base64-decode to 16/24/32 bytes (`openssl rand -base64 32`); validated at startup (`AppProperties`) so a malformed key fails the boot, not the first register. Must be **identical everywhere and stable** — data encrypted under one key can't be decrypted under another. Set it via Secret Manager, not an unquoted shell var (a `$` in the value gets mangled by shell interpolation → invalid Base64).
- `RSA_PUBLIC_KEY`, `RSA_PRIVATE_KEY` — RSA keypair used to sign/verify JWTs.
- `RESEND_API_KEY` — transactional email.
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `APP_ENV` (`dev` default) — optional overrides.

## Architecture map (`src/main/java/com/ojo/cyrus`)

- `controllers/` — REST endpoints: `AuthController`, `CustomerController`, `MerchantController`, `EmailVerificationController`, `WebhookController` (see gap note below).
- `services/` (+ `services/impl`) — business logic: `AuthService`, `CustomerService` (create/get + `getStatement` — customer identity + paginated transaction history), `MerchantService`, `ApiKeyService`, `TokenService`, `EmailService`/`ResendEmailService`, `MerchantUserDetailsService`, `TransactionIngestionService`, `ReconciliationService`, `MerchantWebhookService` (outbox creation), `MerchantWebhookDispatcher` (JobRunr delivery job, retry/backoff orchestration), `MerchantWebhookClient` (signs + POSTs, no DB/JobRunr concerns).
- `nomba/` — provider integration: `NombaClient`, `NombaAuthenticationService` (token acquisition/refresh + cache), `NombaWebhookAdapter`, `service/NombaWebhookService`, `service/NombaSignatureService`, `utils/CredentialMapper`, `utils/NombaCurrencyUtil` (canonical naira→kobo), `NombaCredentials`, `dto/`.
- `models/entities/` — JPA entities: `Merchant`, `Customer` (has `status`/`kycTier`, mutable via field-level `@Setter` only — everything else is builder-only/immutable), `VirtualAccount`, `ApiKey`, `Transaction`, `PaymentEvent`, `MerchantWebhookEvent`, `VerificationToken` (all extend `BaseEntity`; `AuditConfig` provides JPA auditing).
- `models/requests/` + `models/responses/` — API DTOs. All responses use the `CyrusApiResponse<T>` envelope with a `ResponseCode`.
- `config/` — `NombaConfig`, `ResendConfig`, `SwaggerConfig`, `AuditConfig`; `config/security/` (auth, below); `config/properties/` — typed `@ConfigurationProperties` (`AppProperties`, `NombaProperties`, `ResendProperties`, `RsaKeyProperties`, `CorsProperties`).
- `enums/` — domain enums: `KycTier`, `CustomerStatus`, `MerchantStatus`, `VirtualAccountStatus`, `EventStatus`, `MatchStatus`, `TransactionStatus`, `ReconciliationOutcome`, `MerchantWebhookStatus`, `MerchantWebhookEventType`, `ApiKeyStatus`, `Environment`, `Provider`, `ResponseCode`.
- `exception/` — `GlobalExceptionHandler` (`@RestControllerAdvice`) + typed exceptions (incl. `InvalidWebhookUrlException` → 400, `InvalidPaymentEventStateException` → 409, `InvalidCustomerStateException` → 409); return errors through it, not ad-hoc responses.
- `utils/` — `CryptoUtil`, `Mapper`, `WebhookUrlValidator` (SSRF guard — rejects loopback/link-local/private/multicast targets for merchant webhook URLs).

## Authentication (two security filter chains — `config/security/SecurityConfig`)

1. **API-key chain** (`@Order(1)`, matches `/v1/customers/**`): server-to-server. `ApiKeyFilter` reads `Authorization: Bearer <key>`, validates via `ApiKeyService`, and sets an `ENVIRONMENT` request attribute. Keys are prefixed `cyrus_test_…` / `cyrus_live_…`; the prefix selects TEST vs LIVE. Stateless.
2. **Default chain** (everything else): JWT via OAuth2 Resource Server, RSA-signed. Merchant dashboard auth. Public paths: `/v1/auth/login`, `/v1/auth/register`, `/v1/auth/verify-email`, swagger/`/docs`/`/v3/api-docs/**`, `/actuator/health`. Passwords hashed with **BCrypt**.

The API-key chain's `securityMatcher` currently lists only `/v1/customers/**`, but **more API-key-protected endpoints are expected as we build.** To add one, extend the `securityMatcher(...)` path list in the `apiKeyChain` bean in `SecurityConfig` — a new path is *not* covered by the API-key filter until it's added there (it would otherwise fall through to the JWT default chain). Dashboard endpoints stay on the JWT default chain.

## Secrets & crypto (`utils/CryptoUtil`)

- **Nomba credentials are encrypted at rest** with **AES-256-GCM** (`encrypt`/`decrypt`, 12-byte random IV prepended, 128-bit tag) keyed by `APP_ENCRYPTION_KEY`. Never store or log provider credentials in plaintext.
- `hmacSha256(...)` exists for verifying inbound webhook signatures; `sha256(...)` for hashing (e.g. API-key lookup); `randomToken(...)` for token generation.
- **Never commit secrets.** `*.pem`, keys, and `.env` files stay out of git and live in env vars / GCP Secret Manager. If a secret is ever committed, rotate it — deletion alone doesn't scrub git history.

## Data / schema — read before touching persistence

- Schema is currently managed by **Hibernate `ddl-auto: update`** (`application.yaml`). Entities are the source of truth for the schema right now.
- **Gotcha: `ddl-auto: update` adds new columns/tables but never touches an existing CHECK constraint.** Adding a new constant to an `@Enumerated(STRING)` enum (e.g. a new `EventStatus`) does NOT update the DB's existing `..._status_check` constraint to allow it — the app compiles and starts fine, then the very first insert/update using the new constant throws `ConstraintViolationException` at the DB. After adding an enum constant, manually `ALTER TABLE ... DROP CONSTRAINT ..._check` + re-add it with the full new value list on any environment with pre-existing data (local dev DB, etc.) — Flyway will make this a normal migration once enabled.
- **Flyway is a dependency but is DISABLED** (`spring.flyway.enabled: false`) and `src/main/resources/db/migration/` is **empty** — do **not** assume migrations exist or run. This is intentional while the schema is still churning; **Flyway will be switched on near submission.** When that happens: enable it, add a baseline migration reflecting the current entities, switch `ddl-auto` to `validate` (or `none`), and update this section.
- `open-in-view: false` — no lazy loading outside a transaction; fetch what you need in the service layer.

## Current state & known gaps (important)

The judged surfaces (webhook reliability, reconciliation) are implemented and live-verified; the
remaining gaps are noted inline below:

- **Webhook ingestion is implemented and live-verified.** `NombaWebhookController` → `NombaWebhookService` (HMAC verify → adapt → ingest) → `TransactionIngestionService` records a `PaymentEvent` (idempotent by `requestId`) and, only for a genuine VA credit, a `Transaction` (deduped by provider tx id + `sessionId` captured for requery-based reconciliation). Handles both payload shapes: `payment_success` VA transfers (attributed via `transaction.aliasAccountNumber`) and non-VA/failed events like POS `payment_failed` (recorded as `PaymentEvent` `IGNORED`, no transaction). Signature verification extracts all signed fields null-safely (some payloads omit `merchant.walletId`) using `nomba.webhook-secret`; the endpoint is public (HMAC-authenticated via header check). **Critical for reliability:** the controller returns 2xx for signature mismatches, duplicates, orphans, and non-credit events to stop Nomba's 5-retry storm; only transient (DB/network) failures return non-2xx to trigger retries.
- **The reconciliation engine is implemented and live-verified.** A per-transaction JobRunr job (`ReconciliationService.reconcileTransactionById`, scheduled `afterCommit` by `TransactionIngestionService` after a delay of `app.reconciliation.delay-seconds`) requeries Nomba (`GET /v1/transactions/requery/{sessionId}`) as the source of truth, promotes PENDING→SUCCESSFUL, and sets `matchStatus` MATCHED/DISCREPANCY. An unconfirmed session self-reschedules with backoff up to `app.reconciliation.max-attempts`, then flags `MANUAL_REVIEW`. Nomba/DB errors propagate so JobRunr's own retry handles transient failures. Not yet covered: a missing-payment sweep (webhook never delivered) and duplicate-`sessionId` detection.
- **Outbound merchant webhook emission (Cyrus → developer) is implemented.** When a transaction reaches a terminal state, `MerchantWebhookService.recordAndScheduleDispatch` writes a `MerchantWebhookEvent` outbox row in the SAME transaction as the status change (idempotent per transaction+event type, defensively re-checked via `DataIntegrityViolationException` for the concurrent-duplicate-webhook race) and schedules a JobRunr dispatch job `afterCommit`. `MerchantWebhookClient` HMAC-signs `timestamp + "." + payload` (`X-Cyrus-Signature: sha256=<hex>` via `CryptoUtil.hmacSha256`, `X-Cyrus-Timestamp` carrying that same timestamp — binding it into the signed content, not just a sibling header, is what makes replay-window checking on the merchant's side meaningful) and POSTs to the merchant's per-environment URL via the dedicated `merchantWebhookRestClient` (NOT `nombaRestClient`); registered URLs are validated against loopback/link-local/private/multicast ranges (`utils/WebhookUrlValidator`) as an SSRF guard, since Cyrus's own backend makes the request. `MerchantWebhookDispatcher` orchestrates (load plan, retry/backoff decision, persist outcome), classifying failures as retryable (5xx, 429, timeouts/DNS/connection errors) vs. permanent (other 4xx — fails immediately, doesn't burn the retry schedule), recording DELIVERED / RETRYING (exponential backoff up to `app.webhook.max-attempts`) / FAILED. Events: `payment.succeeded` (PENDING→SUCCESSFUL — both MATCHED and DISCREPANCY), `payment.reversed` (REVERSED), `payment.flagged` (MANUAL_REVIEW). Per-environment config (URL + Cyrus-generated `whsec_` secret, stored encrypted) lives on `Merchant.webhookConfigs`, managed via `/v1/merchants/me/webhooks`.
- **Customer statement/reporting is implemented.** `GET /v1/customers/{reference}/statement` (api-key chain) returns identity (`Customer.status`/`kycTier` — `CustomerStatus`/`KycTier` enums, mutable via field-level `@Setter` only) plus lifetime `SUCCESSFUL` volume and a paginated, newest-first transaction history (`CustomerService.getStatement`, merchant-scoped).
- **Misdirected-payment recovery is implemented.** `NormalizedPaymentEvent.walletId` (from Nomba's `data.merchant.walletId` — a sibling of `data.transaction`, not nested inside it) resolves the owning `Merchant` via `MerchantRepository.findByNombaWalletId` (matches `nombaParentAccountId` OR any `nombaSubAccountIds` member — a VA's transfers settle into whichever sub-account wallet it's attached to, not necessarily the parent) independent of virtual-account attribution, so `PaymentEvent.merchant` is set even for an orphan (unknown VA) payment. `PaymentEventController` (JWT chain, merchant-scoped via `jwt.getSubject()` → `MerchantService.findByBusinessEmail`) exposes `GET /v1/admin/payment-events` (list, optional status/provider filter), `GET /{id}` (detail, includes raw payload), `POST /{id}/replay`, and `POST /{id}/reattribute` (`TransactionIngestionService.reattribute` — mints a `Transaction` against a merchant-chosen customer, ignoring the payload's own wrong/unresolvable VA number, then feeds the normal reconciliation pipeline; rejects a target customer that isn't `ACTIVE`). All four are ownership-checked (`PaymentEventService.findByIdForMerchant`) — not-found and not-yours both 404 identically. A payment whose wallet AND virtual account are both unrecognized has no merchant to scope it to and isn't visible to anyone except direct DB/ops access.
- **Customer identity edge cases (rename, KYC tier, suspend/close) are implemented and live-verified.** All three update the existing `Customer`/`VirtualAccount` rows in place — never delete+recreate, never touch `reference` (the merchant's stable identity key) or re-provision a VA. `PATCH /v1/customers/{reference}` (`CustomerService.rename`) updates firstName/lastName/email/phoneNumber; when firstName/lastName changes, it also renames the VA's Nomba-side bank account holder name to match via **`NombaClient.updateVirtualAccountName`** (`PUT /v1/accounts/virtual/{accountRef}`, live-verified against the sandbox — Nomba's schema also accepts `newAccountRef`/`callbackUrl`/`expectedAmount`, which `NombaUpdateVirtualAccountRequest`'s `@JsonInclude(NON_NULL)` deliberately never touches). Two-phase like the status endpoint below: the Nomba call runs with no DB tx open and must succeed before any local field is persisted. Email/phoneNumber-only changes never call Nomba at all (verified). `POST /v1/customers/{reference}/kyc-tier` (`updateKycTier`) is an unguarded tier set — Cyrus doesn't verify KYC itself, that's the merchant's own process; the VA is completely untouched by a tier change. `PATCH /v1/customers/{reference}/status` (`updateStatus`) cascades `CustomerStatus` to the 1:1 `VirtualAccount.status`: ACTIVE⇄SUSPENDED is reversible and Cyrus-local only; CLOSED is terminal (the soft-delete state — row/VA/full transaction history stay intact, but `InvalidCustomerStateException`→409 blocks any further transition) and additionally calls **`NombaClient.expireVirtualAccount`** (`DELETE /v1/accounts/virtual/{accountRef}`, live-verified against the sandbox) to permanently expire the VA on Nomba's side — `accountRef` is `Customer.reference` itself (the same value sent as `accountRef` at creation, which Nomba echoes back on every webhook as `aliasAccountReference`). The Nomba call happens with no DB transaction open and must succeed before the local CLOSED status is ever persisted (two-phase pattern, `Propagation.NOT_SUPPORTED` — see `MerchantService.goLive` for the same pattern), so a failed expiry leaves the customer in its prior state rather than a split-brain CLOSED-locally-but-live-on-Nomba state. **`TransactionIngestionService.ingest()` routes a payment landing on a non-ACTIVE VA into the orphan/reattribution flow above** (never silently attributed to an inactive customer) — live-verified: suspend → mock payment → confirmed `IGNORED` with no `Transaction` → reattribute correctly rejected (409) while suspended → reactivate → reattribute succeeds.
  - **Known gap:** the unique `(merchant_id, reference)` constraint on `customers` doesn't account for status, so a `reference` can never be reused after `CLOSED` — reusing it would need a partial-unique-index redesign, not built.
- **Tests are effectively absent** — only `CyrusApplicationTests` (context-load smoke test), `CryptoUtilTest`, and `NombaWebhookAdapterTest`. Add real coverage for anything reconciliation- or money-related.

Reconciliation accuracy and developer API quality remain the priority surface.

## Conventions

- Wrap all responses in `CyrusApiResponse<T>` with a `ResponseCode`; surface errors via `GlobalExceptionHandler` and the typed exceptions in `exception/`.
- **Error contract:** failures use the `CyrusApiResponse` envelope (`code`, `description`, `message`, `status:false`, `timestamp`). The `data` field carries an `ErrorDetails` **only when it adds something beyond the envelope** — `fieldErrors` on validation (400) or a `traceId` on server/upstream errors (5xx); plain client errors have no `data`. Handlers log the actual throwable and return a **client-safe message** — never leak stack traces or raw provider payloads. Client (4xx) errors log the exception at `warn`; 5xx errors log at `error` with a short `traceId` echoed to the caller for support correlation. **Add a handler here rather than catching in services**, and don't log-before-throwing in services: the handler always logs the throwable, so a bare `throw` is enough to be diagnosable.
- Use the `config/properties` `@ConfigurationProperties` types for config access — don't scatter `@Value` reads.
- Prefer the provider abstraction (`nomba/`, `Provider` enum) over hard-coding Nomba specifics when adding provider-facing logic.
- **Never hold a DB transaction open across an external (Nomba) HTTP call** — it ties up the connection and holds locks for the provider's latency. Pattern: resolve/**materialize** what the call needs inside a short read-only tx (see `MerchantService.getNombaCredentials`), make the HTTP call with no tx open, then persist results in a separate short tx. Use `TransactionTemplate` (not a self-invoked `@Transactional` method — Spring's proxy ignores self-calls and non-public methods). Watch `Merchant`'s lazy `@ElementCollection`s (`nombaCredentials`, `nombaSubAccountIds`): with `open-in-view: false` they must be materialized before the tx closes or you'll hit `LazyInitializationException` (`CredentialMapper.fromMerchant` copies them for this reason).
- **Money uses `BigInteger`, always in minor units (kobo) — never naira, never floating-point.** ₦1 = 100 kobo; represent, compute, and persist every amount as integer kobo in a `BigInteger`. Do **not** use `float`/`double` for money, and do **not** store major-unit naira. Convert to naira only at the display edge (e.g. frontend), never in stored values or arithmetic.
  - **Provider boundary:** Nomba reports amounts in **naira** as a decimal string (e.g. `"281946.0"`), so scale ×100 to kobo when data crosses into Cyrus — the canonical conversion is `NombaCurrencyUtil.nairaToKobo(...)` (`BigDecimal.movePointRight(2)` → `HALF_EVEN` → `BigInteger`), used by webhook ingestion, requery, and balance checks alike. Convert via that single helper, don't re-implement it.
- Lombok is enabled; match the existing annotation style.

## Repo layout note

- Root = Spring Boot backend (this file's scope).
- `web/` = independent Next.js 16 app — has its own agent instructions; don't apply backend build/test commands there.