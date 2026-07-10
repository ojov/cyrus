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
are missing or malformed.

Once running:
- API: `http://localhost:8080`
- API reference (Scalar): `http://localhost:8080/docs`

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

## 📄 Architecture & Security

See [`ARCHITECTURE_AND_SECURITY.md`](./ARCHITECTURE_AND_SECURITY.md) — a high-level overview of the Cyrus architecture,
covering authentication, webhook security, and data handling.

---

## 🔐 Security & Authentication

### JWT (merchant dashboard)
Delivered as an **httpOnly cookie** on `/v1/auth/login` / `/v1/auth/register` — never exposed to
frontend JS or returned in a JSON body. `Authorization: Bearer <token>` also works for direct API
testing (Postman/curl).

### API key (server-to-server)
- **Header:** `Authorization: Bearer <API_KEY>`
- **Format:** `cyrus_...` — one key per merchant, scoped to `/v1/customers/**` today; see `AGENTS.md`
  for how new API-key-protected routes get added.

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
├── Dockerfile              # Container definition
├── compose.yaml            # Local Postgres
├── pom.xml                 # Maven dependencies & build config
└── README.md               # You are here
```

---

## ☁️ Production Deployment (GCP Cloud Run)

The API runs on Cloud Run (`cyrus-api`, project `nombacyrus`, region `us-central1`), served at **https://api.trycyrus.app**. CI (`.github/workflows/deploy.yml`) auto-deploys on push to `main`: it builds the image, then applies **`service.yaml`** — the single source of truth for runtime config — via `gcloud run services replace`.

**Required secrets** (Secret Manager, mapped in `service.yaml`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_ENCRYPTION_KEY`, `RSA_PRIVATE_KEY`, `RSA_PUBLIC_KEY`, `RESEND_API_KEY`, `NOMBA_WEBHOOK_SECRET`. Non-secret config (`APP_ENV`, `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`) is plain env in the same file.

### Manual deploy (single line — copy the whole block)

```bash
sed "s|IMAGE_PLACEHOLDER|us-central1-docker.pkg.dev/nombacyrus/cyrus/cyrus-api:latest|g" .github/deploy/service.yaml | gcloud run services replace - --region us-central1
```

### Restore env/secrets without a code deploy (single line — copy the whole block)

```bash
gcloud run services update cyrus-api --region us-central1 --set-secrets "DB_URL=DB_URL:latest,DB_USERNAME=DB_USERNAME:latest,DB_PASSWORD=DB_PASSWORD:latest,APP_ENCRYPTION_KEY=APP_ENCRYPTION_KEY:latest,RSA_PRIVATE_KEY=RSA_PRIVATE_KEY:latest,RSA_PUBLIC_KEY=RSA_PUBLIC_KEY:latest,RESEND_API_KEY=RESEND_API_KEY:latest,NOMBA_WEBHOOK_SECRET=NOMBA_WEBHOOK_SECRET:latest" --set-env-vars "^@^APP_ENV=prod@APP_BASE_URL=https://api.trycyrus.app@CORS_ALLOWED_ORIGINS=https://trycyrus.app,http://localhost:3000"
```

> These are **one physical line each** — no backslashes — so they paste cleanly (the earlier failure was a trailing space after a `\` line-continuation).

> **Note:** `service.yaml` uses the Knative schema (`serving.knative.dev/v1`) — that's just Cloud Run's declarative config format. **We are not running Kubernetes.**

### Adding a new secret

```bash
# 1. Create the secret (or add a new version to an existing one)
echo -n "the-value" | gcloud secrets create SOME_NEW_KEY --data-file=- --replication-policy=automatic

# 2. Grant the runtime service account read access
gcloud secrets add-iam-policy-binding SOME_NEW_KEY \
  --member="serviceAccount:cyrus-api-runtime@nombacyrus.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

3. Add it to `service.yaml` under the container's `env:`
   ```yaml
   - name: SOME_NEW_KEY
     valueFrom:
       secretKeyRef: { name: SOME_NEW_KEY, key: latest }
   ```
4. Reference `${SOME_NEW_KEY}` in `application.yaml`, then deploy (push to `main`, or the manual replace above).

## 🤝 Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📞 Support & Contact

- **Support Email:** [oticconsults@gmail.com](mailto:support@cyrusmobile.com)
- **Website:** [https://trycyrus.app](https://trycyrus.app)
- **Status Page:** [https://status.trycyrus.app](https://status.trycyrus.app)

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.

---
<p align="center">Built with ❤️ by the Otic Technologies</p>
