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

- `APP_ENCRYPTION_KEY` — base64 AES key; **encrypts Nomba credentials at rest**. Required.
- `RSA_PUBLIC_KEY`, `RSA_PRIVATE_KEY` — RSA keypair used to sign/verify JWTs.
- `RESEND_API_KEY` — transactional email.
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `APP_ENV` (`dev` default) — optional overrides.

## Architecture map (`src/main/java/com/ojo/cyrus`)

- `controllers/` — REST endpoints: `AuthController`, `CustomerController`, `MerchantController`, `EmailVerificationController`, `WebhookController` (see gap note below).
- `services/` (+ `services/impl`) — business logic: `AuthService`, `CustomerService`, `MerchantService`, `ApiKeyService`, `TokenService`, `EmailService`/`ResendEmailService`, `MerchantUserDetailsService`.
- `nomba/` — provider integration: `NombaClient`, `NombaAuthenticationService` (token acquisition/refresh + cache), `CredentialMapper`, `NombaCredentials`, `dto/`.
- `models/entities/` — JPA entities: `Merchant`, `Customer`, `VirtualAccount`, `ApiKey`, `Transaction`, `PaymentEvent`, `VerificationToken` (all extend `BaseEntity`; `AuditConfig` provides JPA auditing).
- `models/requests/` + `models/responses/` — API DTOs. All responses use the `CyrusApiResponse<T>` envelope with a `ResponseCode`.
- `config/` — `NombaConfig`, `ResendConfig`, `SwaggerConfig`, `AuditConfig`; `config/security/` (auth, below); `config/properties/` — typed `@ConfigurationProperties` (`AppProperties`, `NombaProperties`, `ResendProperties`, `RsaKeyProperties`, `CorsProperties`).
- `enums/` — domain enums: `KycTier`, `MerchantStatus`, `VirtualAccountStatus`, `EventStatus`, `MatchStatus`, `ApiKeyStatus`, `Environment`, `Provider`, `ResponseCode`.
- `exception/` — `GlobalExceptionHandler` (`@RestControllerAdvice`) + typed exceptions; return errors through it, not ad-hoc responses.
- `utils/` — `CryptoUtil`, `Mapper`.

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
- **Flyway is a dependency but is DISABLED** (`spring.flyway.enabled: false`) and `src/main/resources/db/migration/` is **empty** — do **not** assume migrations exist or run. This is intentional while the schema is still churning; **Flyway will be switched on near submission.** When that happens: enable it, add a baseline migration reflecting the current entities, switch `ddl-auto` to `validate` (or `none`), and update this section.
- `open-in-view: false` — no lazy loading outside a transaction; fetch what you need in the service layer.

## Current state & known gaps (important)

The scaffolding is ahead of the implementation in exactly the judged areas:

- **`WebhookController` is entirely commented out.** Inbound Nomba webhook ingestion, signature verification, dedup/idempotency, `PaymentEvent` recording, async VA matching, and merchant webhook emission are **not implemented** — they exist only as a documented TODO in that file. The entities/enums (`PaymentEvent`, `Transaction`, `MatchStatus`, `EventStatus`) are placeholders for it.
- **The reconciliation engine does not exist yet.** No job compares provider records vs. internal `Transaction`s for missing/duplicate/orphan/mismatched payments.
- **Tests are effectively absent** — only `CyrusApplicationTests` (context-load smoke test). Add real coverage for anything reconciliation- or money-related.

Treat webhook reliability + reconciliation as the priority surface, and don't describe them as working.

## Conventions

- Wrap all responses in `CyrusApiResponse<T>` with a `ResponseCode`; surface errors via `GlobalExceptionHandler` and the typed exceptions in `exception/`.
- Use the `config/properties` `@ConfigurationProperties` types for config access — don't scatter `@Value` reads.
- Prefer the provider abstraction (`nomba/`, `Provider` enum) over hard-coding Nomba specifics when adding provider-facing logic.
- **Never hold a DB transaction open across an external (Nomba) HTTP call** — it ties up the connection and holds locks for the provider's latency. Pattern: resolve/**materialize** what the call needs inside a short read-only tx (see `MerchantService.getNombaCredentials`), make the HTTP call with no tx open, then persist results in a separate short tx. Use `TransactionTemplate` (not a self-invoked `@Transactional` method — Spring's proxy ignores self-calls and non-public methods). Watch `Merchant`'s lazy `@ElementCollection`s (`nombaCredentials`, `nombaSubAccountIds`): with `open-in-view: false` they must be materialized before the tx closes or you'll hit `LazyInitializationException` (`CredentialMapper.fromMerchant` copies them for this reason).
- **Money uses `BigInteger`, always in minor units (kobo) — never naira, never floating-point.** ₦1 = 100 kobo; represent, compute, and persist every amount as integer kobo in a `BigInteger`. Do **not** use `float`/`double` for money, and do **not** store major-unit naira. Convert to naira only at the display edge (e.g. frontend), never in stored values or arithmetic.
  - **Provider boundary:** Nomba reports amounts in **naira** as a decimal string (e.g. `"281946.0"`), so scale ×100 to kobo when data crosses into Cyrus — see `NombaBalanceData.amountInKobo()` for the canonical conversion (`BigDecimal.movePointRight(2)` → `HALF_EVEN` → `BigInteger`). Apply the same conversion to Nomba webhook/transaction amounts when that ingestion is built.
- Lombok is enabled; match the existing annotation style.

## Repo layout note

- Root = Spring Boot backend (this file's scope).
- `web/` = independent Next.js 16 app — has its own agent instructions; don't apply backend build/test commands there.
