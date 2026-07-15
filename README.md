# 🛡️ Cyrus

[![Java Version](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Cyrus is a **customer payment identity infrastructure layer** built on Nomba. It gives each of your
customers a persistent identity and a dedicated virtual bank account, holds the resulting funds in a
per-merchant wallet (double-entry ledger), and lets you pay them back out — without your business ever
touching Nomba credentials directly. Cyrus holds one Nomba account; every merchant on Cyrus gets a
single API key, a wallet, and a webhook config.

**Dashboard:** [https://trycyrus.app](https://trycyrus.app) — ops dashboard + developer documentation

**API Reference:** [https://api.trycyrus.app/docs](https://api.trycyrus.app/docs) — live Scalar UI,
test every endpoint directly with your API key via the "Authorize" button

**Demo:** [Watch on YouTube](https://youtu.be/65M7bqUCou8)

---

## 🧪 Reviewer Quick Start

Jump straight in without creating an account:

| Resource | Value |
|---|---|
| **Login email** | `oticconsults@gmail.com` |
| **Login password** | `A4@cyrusprod` |
| **API key** | `cyrus_sa4uxH7x9i3akfhnzvWtagjSodBRIM0FsqO5Q25jHtk` |
| **API Reference** | [https://api.trycyrus.app/docs](https://api.trycyrus.app/docs) |

Test the API key directly in the Scalar UI at [https://api.trycyrus.app/docs](https://api.trycyrus.app/docs)

**These credentials are a regular merchant account** — they give you the merchant dashboard and an
API key scoped to `/v1/customers/**`, `/v1/transactions/**`, and `/v1/payment-events/**`. They do
**not** unlock the separate super-admin role (see below), which isn't exposed as a reviewer credential.

---

## 🛡️ Super Admin

A `Merchant` has a `role`: almost every account is `MERCHANT`; `SUPER_ADMIN` is Cyrus's own platform
staff, promoted by listing their business email in `APP_SUPER_ADMIN_EMAILS` at startup (no separate
signup flow). It unlocks platform-wide oversight endpoints (JWT chain, `/v1/platform/**`) that a
regular merchant gets a 403 from — these look *across* merchants rather than at one merchant's own
data, which is why they can't just be more fields on the regular dashboard:

| Endpoint | Purpose |
|---|---|
| `GET /v1/platform/overview` | Custody check — total wallet liabilities vs. the live Nomba balance, reconciliation health, orphaned/stuck items |
| `GET /v1/platform/profit` | Platform profit ledger — expected vs. actual provider balance, accrued fees |
| `GET /v1/platform/virtual-accounts/audit` | Diffs Cyrus's local virtual-account records against Nomba's live list — catches a VA that leaked on Nomba's side with no local record |
| `GET/PUT /v1/platform/fees` | Read/update the platform-wide fee configuration |
| `GET /v1/platform/orphans`, `POST /v1/platform/orphans/{id}/reattribute` | Payments that couldn't be attributed to *any* merchant's virtual account — reattribute once the right merchant/customer is identified |

---

## ✨ Key Features

- **🏦 Virtual account provisioning** — a dedicated Nomba virtual account per customer, created and
  managed under Cyrus's own platform account.
- **🔁 Inbound reconciliation** — every webhook is verified against Nomba's own requery endpoint
  (source of truth) before a transaction is confirmed and a wallet credited.
- **💰 Wallet & payouts** — double-entry ledger per merchant; pay beneficiaries out via bank transfer.
- **🔐 Dual authentication** — JWT (httpOnly cookie) for the merchant dashboard, API keys for
  server-to-server integrations.
- **🧭 Misdirected-payment recovery** — orphaned/misattributed payments are visible and can be
  manually reattributed to the right customer.
- **🛠️ Developer-first** — `CyrusApiResponse` envelope, typed errors, and a live Scalar API reference.

---

## 🛠️ Technology Stack

- **Framework:** Spring Boot 4.1.0 · **Language:** Java 25
- **Security:** Spring Security (OAuth2 Resource Server, RSA-signed JWT) + API-key auth
- **Database:** PostgreSQL + Spring Data JPA/Hibernate + Flyway (schema migrations, `ddl-auto: validate`)
- **Background jobs:** JobRunr (outbound merchant webhook delivery retries only — reconciliation runs
  via `@Async`/`@Scheduled` on virtual threads, not JobRunr)
- **Email:** Resend · **API docs:** springdoc OpenAPI + Scalar
- **Infrastructure:** Docker Compose (local Postgres), deployed on GCP Cloud Run

---

## 🚦 Getting Started

### Prerequisites

- **Java 25**, **Maven 3.6+**, **Docker** (for local Postgres)

### Local setup

```bash
git clone <repository-url>
cd cyrus
docker compose up -d          # starts local Postgres on :5438
./mvnw spring-boot:run        # API on :8080
```

Required environment variables are listed in `AGENTS.md` under **Required configuration** (encryption
key, RSA keypair, DB credentials, Resend key, Nomba credentials). The API fails fast at startup if any
are missing or malformed. For the full walkthrough — every env var, generating the RSA keypair
correctly, auth flow for local testing, project layout, troubleshooting — see
[`docs/LOCAL_SETUP.md`](./docs/LOCAL_SETUP.md).

Once running:
- API: `http://localhost:8080`
- API reference (Scalar): `http://localhost:8080/docs`

---

## 📚 Documentation

Internal reference docs live in [`docs/`](./docs) — start at [`docs/README.md`](./docs/README.md) for
the full index. Highlights:

- [`docs/LOCAL_SETUP.md`](./docs/LOCAL_SETUP.md) — running the full stack locally, env vars, auth
  flow, troubleshooting.
- [`docs/DATABASE_RELATIONSHIPS.md`](./docs/DATABASE_RELATIONSHIPS.md) — entity-relationship diagrams,
  the money model, and data-flow sequence diagrams for inbound payments and payouts.
- [`AGENTS.md`](./AGENTS.md) — the living architecture map and conventions doc (also doubles as
  AI-agent guidance).

---

## 📖 API Documentation

The full API surface — every endpoint, request/response schema, and error shape — is generated live
from the running application via Scalar, not hand-maintained here:

- **Local:** `http://localhost:8080/docs`
- **Production:** `https://api.trycyrus.app/docs`

In `dev`, every controller is visible there (dashboard, provider webhook receiver, developer-facing
API). In `prod`, only the public developer-facing surface is exposed — see `springdoc.packages-to-scan`
in `application-prod.yml`.

For a narrative walkthrough (auth, provisioning a customer, webhooks, reconciliation), see the
developer docs at [https://trycyrus.app](https://trycyrus.app).

---

## 📦 Python SDK

A Python SDK is available on PyPI as [`cyrus-payments`](https://pypi.org/project/cyrus-payments/),
covering the developer (API-key) surface — customers, transactions, and payment events.

```bash
pip install cyrus-payments
```

See [`sdk/python/README.md`](./sdk/python/README.md) for usage instructions.

---

## 📄 Architecture & Security

See [`ARCHITECTURE_AND_SECURITY.md`](./ARCHITECTURE_AND_SECURITY.md) — a high-level overview of the Cyrus architecture,
covering authentication, webhook security, and data handling.

---

## 🔐 Security & Authentication

### JWT + Refresh Token (merchant dashboard)
- **Access token** (`cyrus_token`): 15-minute JWT, httpOnly cookie
- **Refresh token** (`cyrus_refresh`): 30-day token, SHA-256 hashed in DB, httpOnly cookie
- Proactive refresh every 14 minutes via `/v1/auth/refresh`
- `Authorization: Bearer <token>` also works for direct API testing (Postman/curl)

### API key (server-to-server)
- **Header:** `Authorization: Bearer <API_KEY>`
- **Format:** `cyrus_...` — one key per merchant, scoped to `/v1/customers/**`, `/v1/transactions/**`, and `/v1/payment-events/**`; see `AGENTS.md` for how new API-key-protected routes get added.

---

## ⚙️ Configuration

See `AGENTS.md` → **Required configuration (env vars)** for the authoritative, up-to-date list
(`APP_ENCRYPTION_KEY`, `RSA_PUBLIC_KEY`/`RSA_PRIVATE_KEY`, `RESEND_API_KEY`, `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD`, `NOMBA_*`, and friends) — kept there rather than duplicated here so it can't drift.

---

## 📂 Project Structure

```text
cyrus/
├── src/
│   ├── main/
│   │   ├── java/           # Spring Boot source code (see AGENTS.md for the architecture map)
│   │   └── resources/      # App config, migrations, templates
│   └── test/               # Unit and integration tests
├── web/                    # Next.js 16 frontend (dashboard + public developer docs) — own AGENTS.md
├── sdk/python/             # Python SDK (cyrus-payments on PyPI) — own README
├── Dockerfile              # Container definition
├── compose.yaml            # Local Postgres
├── pom.xml                 # Maven dependencies & build config
└── README.md               # You are here
```

---

## 🤝 Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📞 Support & Contact

- **Support Email:** [oticconsults@gmail.com](mailto:oticconsults@gmail.com)
- **Website:** [https://trycyrus.app](https://trycyrus.app)
- **Status Page:** [https://api.trycyrus.app/actuator/health](https://api.trycyrus.app/actuator/health)

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.

---
<p align="center">Built with ❤️ by the Otic Technologies</p>
