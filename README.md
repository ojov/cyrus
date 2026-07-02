# 🛡️ Cyrus Mobile Backend API

[![Java Version](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#)

A robust, enterprise-grade REST API backend for managing virtual accounts and merchant operations. Built with Spring Boot and optimized for deployment on Google Cloud Platform (GCP).

## 🚀 Overview

Cyrus is a powerful merchant dashboard platform that enables businesses to seamlessly create and manage virtual accounts for their customers. Our backend provides a secure, scalable API with dual-mode authentication:
- **Dashboard Access:** JWT-based session management.
- **External Integrations:** Secure API Key authentication for server-to-server communication.

**Live API:** [https://api.trycyrus.app](https://api.trycyrus.app)  
**Documentation:** [https://api.trycyrus.app/swagger-ui/index.html](https://api.trycyrus.app/swagger-ui/index.html)

---

## ✨ Key Features

- **🏦 Virtual Account Provisioning** - Real-time generation of unique bank accounts via Nomba integration.
- **🔐 Dual Authentication** - Secure access via JWT for frontend and API Keys for backend integrations.
- **🧪 Environment Switching** - Built-in support for `TEST` and `LIVE` environments with dedicated API keys.
- **👤 Merchant Management** - Full lifecycle management including registration, verification, and profile updates.
- **📊 Balance Monitoring** - Real-time tracking of parent and sub-account balances.
- **🛠️ Developer First** - Comprehensive Swagger/OpenAPI documentation and standardized error handling.

---

## 🛠️ Technology Stack

- **Framework:** Spring Boot 4.1.0
- **Language:** Java 25
- **Security:** Spring Security (OAuth2 Resource Server)
- **Database:** PostgreSQL with Flyway Migrations
- **Email:** Resend Integration
- **API Docs:** SpringDoc OpenAPI 3.0.2
- **Infrastructure:** Docker & Docker Compose

---

## 🚦 Getting Started

### Prerequisites

- **Java 25** or higher
- **Maven 3.6+**
- **Docker & Docker Compose** (Optional, for containerized setup)

### Installation & Local Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd cyrus
   ```

2. **Configure Environment:**
   Create an `application-local.properties` or set environment variables (see [Configuration](#-configuration)).

3. **Build the project:**
   ```bash
   ./mvnw clean package
   ```

4. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will be available at `http://localhost:8080`.

### 🐳 Docker Deployment

**Using Docker Compose (Recommended):**
```bash
docker-compose up -d
```

**Manual Build:**
```bash
docker build -t cyrus-api .
docker run -p 8080:8080 cyrus-api
```

---

## 📖 API Documentation

### Interactive Swagger UI
Explore the full API surface, schemas, and test endpoints directly:
- **Local:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Production:** [https://api.trycyrus.app/swagger-ui/index.html](https://api.trycyrus.app/swagger-ui/index.html)

### Core Endpoints Preview

#### 1. Authentication
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/v1/auth/register` | Register a new merchant | None |
| `POST` | `/v1/auth/login` | Merchant login (returns JWT) | None |

#### 2. Virtual Accounts (API Key required)
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/v1/customers` | Create customer & provision VA |
| `GET` | `/v1/customers/{ref}` | Retrieve customer details |

#### 3. Merchant Operations (JWT required)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/merchants/me/subaccounts/balances` | Get Nomba balances |
| `PATCH` | `/v1/merchants/me/subaccounts` | Update sub-account IDs |

#### 4. Webhooks (Nomba Integration)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/v1/webhooks/nomba` | Receive payment notifications | Nomba Sig |

---

## 🔐 Security & Authentication

### JWT Authentication
Used primarily for the Merchant Dashboard.
- **Header:** `Authorization: Bearer <JWT_TOKEN>`
- **Obtained via:** `/v1/auth/login` or `/v1/auth/register`

### API Key Authentication
Used for server-to-server integrations.
- **Header:** `Authorization: Bearer <API_KEY>`
- **Format:** `cyrus_test_...` or `cyrus_live_...`
- **Environment:** The system automatically detects the environment (Test/Live) based on the key prefix.

---

## ⚙️ Configuration

The application can be configured via environment variables:

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL Connection URL | `jdbc:postgresql://localhost:5432/cyrus` |
| `SPRING_DATASOURCE_USERNAME` | DB Username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB Password | `postgres` |
| `RESEND_API_KEY` | Resend.com API Key for emails | - |
| `NOMBA_CLIENT_ID` | Nomba Integration Client ID | - |
| `NOMBA_CLIENT_SECRET` | Nomba Integration Secret | - |

---

## 📂 Project Structure

```text
cyrus/
├── src/
│   ├── main/
│   │   ├── java/           # Spring Boot source code
│   │   └── resources/      # App config, migrations, templates
│   └── test/               # Unit and Integration tests
├── web/                    # Static web assets
├── Dockerfile              # Container definition
├── compose.yaml            # Multi-container orchestration
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

- **Support Email:** [support@cyrusmobile.com](mailto:support@cyrusmobile.com)
- **Website:** [https://trycyrus.app](https://trycyrus.app)
- **Status Page:** [https://status.trycyrus.app](https://status.trycyrus.app)

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.

---
<p align="center">Built with ❤️ by the Cyrus Team</p>
