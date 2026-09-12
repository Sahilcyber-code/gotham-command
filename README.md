# GOTHAM COMMAND — Project Operations

Gotham Command is an enterprise-grade project operations and issue tracking platform designed for the teams keeping Gotham moving. Built with a dark, Wayne Enterprises command-center aesthetic, it features robust multi-tenant organization boundaries, hierarchical role-based access control (RBAC), and high-reliability data persistence.

---

## 🏛️ System Architecture

- **Frontend**: React 19, Vite, TanStack Query, Axios, Tailwind CSS, Lucide icons.
- **Backend**: Spring Boot 3.3 (Java 21), Spring Security, Spring Data JPA / Hibernate.
- **Database**: PostgreSQL 16 with Flyway schema migration management.
- **Authentication**: Google OAuth 2.0 with short-lived in-memory JWT access tokens and HttpOnly, SameSite-secured refresh tokens.
- **Infrastructure**: Docker Compose, Nginx reverse proxy.

---

## 📋 Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [Environment Variables](#2-environment-variables)
3. [Local Development](#3-local-development)
4. [Docker Startup](#4-docker-startup)
5. [Frontend Startup](#5-frontend-startup)
6. [Backend Startup](#6-backend-startup)
7. [Google OAuth 2.0 Setup](#7-google-oauth-20-setup)
8. [Database & Flyway Migrations](#8-database--flyway-migrations)
9. [Test Commands](#9-test-commands)
10. [Production Configuration Overview](#10-production-configuration-overview)

---

## 1. Prerequisites
Ensure the following tools are installed on your host system:
- **Node.js**: Version 20+ with `pnpm` (run `corepack enable` to activate).
- **Java Development Kit (JDK)**: Java 21 (e.g., Eclipse Temurin 21).
- **Maven**: Version 3.9+.
- **PostgreSQL**: Version 16 (or run via Docker).
- **Docker & Docker Compose**: (Optional, for containerized deployment).

---

## 2. Environment Variables
Copy the template configuration file:
```bash
cp .env.example .env
```
Key configuration variables:
| Variable | Description | Default (Local) | Production Example |
| :--- | :--- | :--- | :--- |
| `SERVER_PORT` | Port for Spring Boot backend | `8080` | `8080` |
| `DATABASE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/gotham` | `jdbc:postgresql://db.corp:5432/gotham?sslmode=require` |
| `DATABASE_USERNAME` | Database username | `postgres` | `app_user` |
| `DATABASE_PASSWORD` | Database password | `postgres` | `[SECURE_STRONG_PASSWORD]` |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID | `placeholder-id` | `[CLIENT_ID].apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET`| Google OAuth client secret | `placeholder-secret` | `[CLIENT_SECRET]` |
| `GOOGLE_REDIRECT_URI` | Google OAuth redirect URI | `http://localhost:8080/login/oauth2/code/google` | `https://api.yourdomain.com/login/oauth2/code/google` |
| `JWT_SECRET` | HMAC-SHA256 secret (256+ bits) | `change-me-in-production` | `[256_BIT_CRYPTOGRAPHIC_SECRET]` |
| `JWT_EXPIRATION` | Access token TTL in milliseconds | `900000` (15m) | `900000` |
| `CORS_ALLOWED_ORIGINS`| Allowed frontend origins (comma-sep) | `http://localhost:5173,http://127.0.0.1:5173` | `https://gotham.yourdomain.com` |
| `FRONTEND_URL` | Frontend origin for OAuth redirect | `http://localhost:5173` | `https://gotham.yourdomain.com` |
| `REFRESH_COOKIE_SECURE`| Cookie Secure attribute flag | `false` | `true` |
| `VITE_API_URL` | Frontend client REST API URL | `http://localhost:8080/api` | `https://api.yourdomain.com/api` |
| `VITE_BACKEND_URL` | Frontend client base backend URL | `http://localhost:8080` | `https://api.yourdomain.com` |

---

## 3. Local Development
For day-to-day feature testing and development:
1. Ensure a PostgreSQL instance is running on port 5432 (or start via `docker compose up postgres -d`).
2. Start the Spring Boot backend on port 8080.
3. Start the Vite React development server on port 5173.

---

## 4. Docker Startup
To launch the complete isolated production-like stack (PostgreSQL + Spring Boot backend + Nginx-hosted React frontend):
```bash
# Build images without cached layers and start containers in background
docker compose build --no-cache
docker compose up -d

# Verify running services
docker compose ps

# View service logs
docker compose logs -f backend
```
- Frontend UI: `http://localhost:5173`
- Backend REST API: `http://localhost:8080/api`
- Backend Health Check: `http://localhost:8080/api/health`

---

## 5. Frontend Startup
To run the React 19 / Vite frontend independently:
```bash
# Install exact pinned dependencies
pnpm install --frozen-lockfile

# Typecheck TypeScript files
pnpm check

# Start development server with proxy to backend:8080
pnpm dev
```
The application opens at `http://localhost:5173`.

---

## 6. Backend Startup
To compile and launch the Spring Boot REST backend:
```bash
cd backend

# Execute backend with development profile
mvn spring-boot:run
```
The REST API initializes Flyway migrations on startup and binds to `http://localhost:8080`.

For local H2 development with Google OAuth on port 8081, configure the credentials once in the Windows user environment:
```powershell
.\scripts\configure-local-google-oauth.ps1
```
Open a new PowerShell session, then start the backend without exposing credentials in the repository:
```powershell
.\scripts\start-backend-dev.ps1
```

---

## 7. Google OAuth 2.0 Setup
1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select your project and configure the OAuth consent screen with scopes: `openid`, `profile`, `email`.
3. Create **OAuth 2.0 Client IDs** (Application type: Web application).
4. Add Authorized JavaScript origins:
   - Development: `http://localhost:5173`, `http://127.0.0.1:5173`
   - Production: `https://gotham.yourdomain.com`
5. Add Authorized redirect URIs:
   - Development: `http://localhost:8080/login/oauth2/code/google`
   - Production: `https://api.yourdomain.com/login/oauth2/code/google`
6. Copy Client ID and Client Secret into your `.env` file. Detailed instructions are available in [docs/google-oauth-setup.md](docs/google-oauth-setup.md).

---

## 8. Database & Flyway Migrations
PostgreSQL is the authoritative store of record. Hibernate schema generation is set to `ddl-auto: validate` in production, ensuring that schema changes are exclusively managed by Flyway:
- Migrations reside in: `backend/src/main/resources/db/migration/`
- `V1__init.sql`: Base operational bootstrap and health records.
- `V2__gotham_core_schema.sql`: Core schema defining `users`, `organizations`, `organization_members`, `projects`, `project_members`, `issues`, `comments`, `refresh_tokens`, indexes, and cascading foreign keys.

To run migrations manually via Maven:
```bash
cd backend
mvn flyway:migrate
```

---

## 9. Test Commands

### Backend Automated Test Suite (85+ Tests)
Runs comprehensive integration tests for RBAC, cascade deletions, cross-tenant isolation, refresh token lifecycle, and OAuth security:
```bash
cd backend
mvn clean test
```

### Full Production Package (Builds Executable JAR)
```bash
cd backend
mvn clean package -DskipTests=false
```

### Frontend Typecheck & Production Bundle
```bash
# TypeScript verification (zero emit)
pnpm check

# Vite production build (outputs to dist/public)
pnpm build
```

---

## 10. Production Configuration Overview
When deploying Gotham Command to production:
1. **Enforce HTTPS Everywhere**: Terminate TLS at the load balancer or Nginx proxy. Set `REFRESH_COOKIE_SECURE=true`.
2. **Restrict CORS**: Configure `CORS_ALLOWED_ORIGINS` to contain ONLY your exact frontend domains. Never wildcard origins when credentials are enabled.
3. **Protect Database Access**: PostgreSQL should run on an internal private network without public internet accessibility.
4. **Secret Storage**: Store `JWT_SECRET`, `DATABASE_PASSWORD`, and `GOOGLE_CLIENT_SECRET` in a dedicated secret store (e.g., HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager).
5. **Database Backups**: Follow the automated `pg_dump` and continuous WAL archiving procedures detailed in [docs/architecture.md](docs/architecture.md).
6. **Health Monitoring**: Monitor `GET /api/health` with your container orchestrator (Kubernetes liveness/readiness probes or Docker healthcheck).

---

## 🛡️ Documentation Links
- [Security Architecture & Hardening](docs/security.md)
- [System Architecture & Backup Safety](docs/architecture.md)
- [Google OAuth 2.0 Setup](docs/google-oauth-setup.md)
- [Role-Based Access Control (RBAC)](docs/rbac.md)
