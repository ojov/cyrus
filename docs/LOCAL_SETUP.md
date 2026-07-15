# Local Development Setup

> How to run the Cyrus backend locally. The `web/` dashboard (Next.js) is a separate app with its own setup — see `web/README.md`.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **Java** | 25 | Oracle/OpenJDK 25 |
| **Maven** | 3.6+ | Use the bundled `./mvnw` wrapper (no global install needed) |
| **Docker** | Any recent version | For local PostgreSQL |
| **Git** | Any version | |

---

## 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 17 on **port 5438** (non-standard — avoids clashing with other local Postgres instances).

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `5438` |
| Database | `cyrus` |
| User | `cyrus` |
| Password | `cyrus_password` |

The `spring-boot-docker-compose` dependency is on the runtime classpath, so `./mvnw spring-boot:run` can also auto-start the DB container if Docker is running.

---

## 2. Set Environment Variables

Create a `.env` file in the project root or export these in your shell. At minimum, you need:

```bash
# Database (matches compose.yaml defaults)
export DB_URL="jdbc:postgresql://localhost:5438/cyrus"
export DB_USERNAME="cyrus"
export DB_PASSWORD="cyrus_password"

# Encryption key — AES-256-GCM, must Base64-decode to 16/24/32 bytes
# Generate one with: openssl rand -base64 32
export APP_ENCRYPTION_KEY="<your-base64-32-byte-key>"

# RSA keypair for signing/verify JWTs — the public key MUST be derived from the same private key,
# not generated separately (two independent `genpkey` calls produce unrelated keys, which silently
# breaks JWT verification):
#   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private_key.pem
#   openssl rsa -in private_key.pem -pubout -out public_key.pem
export RSA_PUBLIC_KEY="$(cat public_key.pem)"
export RSA_PRIVATE_KEY="$(cat private_key.pem)"

# Nomba platform account credentials — Cyrus runs on a single Nomba account of its own (no more
# per-merchant or TEST/LIVE credential split). ACCOUNT_ID and SUB_ACCOUNT_ID have no default, so the
# app won't boot without them; CLIENT_ID/CLIENT_SECRET default to blank (the app boots either way,
# but every Nomba call fails until they're set).
export NOMBA_ACCOUNT_ID="<your-nomba-account-id>"
export NOMBA_SUB_ACCOUNT_ID="<your-nomba-sub-account-id>"
export NOMBA_LIVE_CLIENT_ID="<your-nomba-client-id>"
export NOMBA_LIVE_CLIENT_SECRET="<your-nomba-client-secret>"
export NOMBA_WEBHOOK_SECRET="<your-nomba-webhook-hmac-secret>"

# Resend (transactional email)
export RESEND_API_KEY="<your-resend-api-key>"

# App config (optional — defaults shown)
export APP_ENV="dev"
export APP_BASE_URL="http://localhost:8080"
export APP_SUPER_ADMIN_EMAILS="you@example.com"
```

> There is no separate sandbox/test credential pair any more — `NOMBA_LIVE_CLIENT_ID`/`NOMBA_LIVE_CLIENT_SECRET` is the one credential set Cyrus authenticates with (the "LIVE" in the name is a holdover from an earlier per-environment design). Leaving them unset lets the app boot, but every Nomba call then fails with a clear "No Nomba platform credentials configured" error — set real values before exercising any VA/payment/payout flow locally.

---

## 3. Run the Application

```bash
./mvnw spring-boot:run
```

The API starts on **http://localhost:8080**.

| Endpoint | URL |
|---|---|
| API root | `http://localhost:8080` |
| API reference (Scalar UI) | `http://localhost:8080/docs` |
| OpenAPI spec (JSON) | `http://localhost:8080/v3/api-docs` |
| Health check | `http://localhost:8080/actuator/health` |
| JobRunr dashboard | `http://localhost:8000` (if enabled) |

---

## 4. Useful Maven Commands

```bash
# Build (compile + run tests)
./mvnw clean package

# Run tests only
./mvnw test

# Run with a specific Spring profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Skip tests during build (fast compile)
./mvnw clean package -DskipTests

# Run Flyway migration manually
./mvnw flyway:migrate
```

---

## 5. Connect to the Local Database

Use any PostgreSQL client with:

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `5438` |
| Database | `cyrus` |
| User | `cyrus` |
| Password | `cyrus_password` |

CLI example:

```bash
psql -h localhost -p 5438 -U cyrus -d cyrus
```

Flyway manages the schema. On first run it executes `V1__baseline.sql` (creates all tables). Subsequent schema changes are new `V{n}__description.sql` files in `src/main/resources/db/migration/`.

---

## 6. Project Layout Reference

```
cyrus/
├── src/main/java/com/ojo/cyrus/
│   ├── controllers/
│   │   ├── developer/       # API-key protected (customers, transactions, payment-events)
│   │   ├── dashboard/       # JWT protected (merchant dashboard)
│   │   └── provider/        # Inbound Nomba webhook receiver
│   ├── services/            # Business logic
│   ├── repositories/        # Spring Data JPA repositories
│   ├── models/
│   │   ├── entities/        # JPA entities (16 tables)
│   │   ├── requests/        # API request DTOs
│   │   ├── responses/       # API response DTOs
│   │   └── dto/             # Internal (non-API) DTOs
│   ├── enums/               # Domain enums
│   ├── nomba/               # Nomba provider integration
│   ├── config/              # Spring config + security filter chains
│   ├── exception/           # Global exception handler
│   └── utils/               # Crypto, money, validation helpers
├── src/main/resources/
│   ├── application.yaml     # Main config
│   ├── application-dev.yml  # Dev profile overrides
│   └── db/migration/        # Flyway SQL migrations
├── web/                     # Next.js 16 dashboard (separate app)
├── sdk/python/              # Python SDK (cyrus-payments on PyPI)
├── compose.yaml             # Local PostgreSQL
├── pom.xml                  # Maven dependencies
└── AGENTS.md                # AI-agent guidance + architecture map
```

---

## 7. Authentication for Local Testing

### JWT (Dashboard)

1. Register: `POST /v1/auth/register` with `{ "businessName": "...", "businessEmail": "you@example.com", "password": "..." }`
2. Verify email (check Resend inboxes if configured, or hit the verify endpoint directly)
3. Login: `POST /v1/auth/login` with `{ "email": "you@example.com", "password": "..." }`
4. Both an access token and refresh token are set as httpOnly cookies.

### API Key (Server-to-Server)

1. Login to the dashboard first (JWT chain)
2. Create an API key: `POST /v1/merchants/me/api-keys`
3. The raw key is shown once — store it securely.
4. Use it: `Authorization: Bearer cyrus_<key>`

### Quick Test

```bash
# Using the API key
curl -H "Authorization: Bearer cyrus_<your-key>" http://localhost:8080/v1/customers

# Using JWT (copy the cyrus_token cookie from a browser login)
curl -b "cyrus_token=<jwt>" http://localhost:8080/v1/merchants/me
```

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `DB_URL` / credentials missing | Ensure all env vars from step 2 are exported in the shell running `mvnw` |
| Port 5438 already in use | `docker compose down` then `docker compose up -d`, or change the port mapping in `compose.yaml` |
| `FlywayValidateException` | Schema drift — run `./mvnw flyway:info` to see which migration is unapplied, or check entity annotations match the DB |
| Nomba calls fail with "No Nomba platform credentials configured" | Ensure `NOMBA_LIVE_CLIENT_ID` and `NOMBA_LIVE_CLIENT_SECRET` are set and valid |
| `APP_ENCRYPTION_KEY` invalid | Must Base64-decode to exactly 16, 24, or 32 bytes. Regenerate: `openssl rand -base64 32` |
| JobRunr dashboard shows no jobs | JobRunr manages its own DB tables (not part of Flyway migrations — this is expected) |
