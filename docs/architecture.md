# Gotham Command — System Architecture

## 1. High-Level Architecture Overview

Gotham Command is an enterprise-grade project operations and issue tracking system built on a unified, authoritative architecture:

```
┌──────────────────────────────────────────────────────────┐
│                   Browser / Client                       │
│        React 19 + Vite + TanStack Query + Tailwind       │
└────────────────────────────┬─────────────────────────────┘
                             │
                             │ REST over HTTPS (Bearer JWT)
                             ▼
┌──────────────────────────────────────────────────────────┐
│                  Nginx Reverse Proxy                     │
│               TLS Termination & Headers                  │
└────────────────────────────┬─────────────────────────────┘
                             │
                             │ Internal Reverse Proxy
                             ▼
┌──────────────────────────────────────────────────────────┐
│              Spring Boot REST API (Java 21)              │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Security Layer (Google OAuth2 + JWT Filter)        │  │
│  ├────────────────────────────────────────────────────┤  │
│  │ REST Controllers (Auth, Org, Project, Issue, Comm) │  │
│  ├────────────────────────────────────────────────────┤  │
│  │ Business Services & RBAC Evaluators                │  │
│  ├────────────────────────────────────────────────────┤  │
│  │ Spring Data JPA Repositories / Hibernate           │  │
│  └────────────────────────────────────────────────────┘  │
└────────────────────────────┬─────────────────────────────┘
                             │
                             │ JDBC
                             ▼
┌──────────────────────────────────────────────────────────┐
│                   PostgreSQL 16 Engine                   │
│          Schema Authority Managed by Flyway             │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Component Layers

### Frontend (`client/src/`)
- **Core Framework**: React 19 with Vite bundler.
- **Routing**: Lightweight, declarative client-side routing via `wouter`.
- **State & Data Caching**: `@tanstack/react-query` for query caching, cache invalidation, and server synchronization.
- **API Communication**: Central Axios client (`client/src/services/api.ts`) featuring:
  - In-memory JWT access token management.
  - Automatic `Authorization: Bearer <token>` attachment.
  - Single-flight refresh token queue with seamless 401 interception.
- **Domain API Modules**:
  - `authApi.ts`: Google login trigger, refresh token exchange, current user profile, logout.
  - `organizationApi.ts`: Organization lifecycle, membership, and RBAC roles.
  - `projectApi.ts`: Project creation, updates, and member assignments.
  - `issueApi.ts`: Issue tracking, board drag-and-drop status changes, subtasks, assignments, sorting.
  - `commentApi.ts`: Real-time issue discussion comments.
- **Design System**: Gotham Command dark aesthetic with Wayne Enterprises command center styling, Radix UI primitives, Lucide icons, and Tailwind CSS.

### Backend (`backend/src/main/java/com/gotham/command/`)
- **Config & Security (`config/`, `security/`)**:
  - `SecurityConfig`: Stateless security filter chain, CORS origin binding, defense-in-depth security headers, and endpoint permission policies.
  - `JwtAuthenticationFilter`: Request-level bearer token extractor and validator.
  - `JwtService`: HMAC-SHA256 token signer and claims parser.
  - `OAuth2AuthenticationSuccessHandler`: Completes Google OAuth login, issues HttpOnly refresh token cookie, and redirects with `?auth=success`.
  - `OAuth2AuthenticationFailureHandler`: Sanitizes OAuth failures to `?error=authentication_failed`.
  - `GlobalExceptionHandler`: Centralized, sanitized REST error responses.
- **Controllers (`controller/`)**:
  - `AuthController`: Profile (`/api/auth/me`), Token Refresh (`/api/auth/refresh`), Logout (`/api/auth/logout`).
  - `OrganizationController`: Full CRUD and membership management.
  - `ProjectController`: Organization-scoped project operations and member rosters.
  - `IssueController`: Project-scoped issue creation, hierarchical subtasks, status transitions, sort ordering, and assignee updates.
  - `CommentController`: Issue-scoped discussion commentary.
- **Services (`service/`)**:
  - `UserService`, `RefreshTokenService`, `OrganizationService`, `ProjectService`, `IssueService`, `CommentService`.
  - Business validations, hierarchy enforcement, audit timestamps, and transactional integrity.
- **Entities & Repositories (`entity/`, `repository/`)**:
  - Strongly typed JPA entities: `User`, `RefreshToken`, `Organization`, `OrganizationMember`, `Project`, `ProjectMember`, `Issue`, `Comment`.
  - Spring Data JPA repositories with custom JPQL queries and pagination support.

---

## 3. Database Schema & Migration Management

PostgreSQL is the single, authoritative persistent source of truth. Schema migrations are strictly governed by **Flyway**:
- Migration scripts reside in `backend/src/main/resources/db/migration/`.
- Hibernate schema auto-generation is set to `ddl-auto: validate` to guarantee that application code never mutates database schemas automatically.
- Constraints, unique indices, cascading deletes, and foreign keys are explicitly defined in Flyway SQL migrations.

---

## 4. Docker Architecture & Topology

The containerized deployment topology is defined in `docker-compose.yml`:

```
                    ┌─────────────────────────┐
                    │      Host Network       │
                    └───────────┬─────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          │ Port 5173           │ Port 8080           │ Port 5432
          ▼                     ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ gotham-frontend  │  │  gotham-backend  │  │ gotham-postgres  │
│  (Nginx Alpine)  │  │  (Spring Boot)   │  │  (Postgres 16)   │
└─────────┬────────┘  └─────────▲────────┘  └─────────▲────────┘
          │                     │                     │
          │ Internal HTTP Proxy │                     │
          └─────────────────────┘                     │
                                │ JDBC Connection     │
                                └─────────────────────┘
```

- **`postgres`**: `postgres:16-alpine` with healthcheck on port 5432 and persistent volume `postgres_data`.
- **`backend`**: Built via `backend/Dockerfile` (OpenJDK 21), depending on postgres health.
- **`frontend`**: Built via multi-stage `Dockerfile` (Node 20 build -> Nginx Alpine runtime) exposing port 5173.

---

## 5. PostgreSQL Backup, Disaster Recovery & Migration Safety

In production environments, persistent data integrity and rapid disaster recovery are critical operational requirements:

### Automated Backup Strategy
1. **Logical Backups (`pg_dump`)**:
   - Schedule daily automated logical database dumps using `pg_dump`:
     ```bash
     pg_dump -Fc -h <db_host> -U postgres -d gotham -f "/backup/gotham_$(date +%Y%m%d_%H%M%S).dump"
     ```
   - Store encrypted dumps offsite (e.g., AWS S3 with Object Lock / versioning enabled, GCP Cloud Storage, or Azure Blob Storage).
   - Retain daily backups for 30 days, weekly backups for 12 weeks, and monthly backups for 1 year.
2. **Physical Continuous Archiving & Point-In-Time Recovery (PITR)**:
   - Enable PostgreSQL Write-Ahead Logging (WAL) archiving:
     ```ini
     wal_level = replica
     archive_mode = on
     archive_command = 'test ! -f /wal_archive/%f && cp %p /wal_archive/%f'
     ```
   - Enables Point-In-Time Recovery to any specific second in the event of accidental data deletion or hardware failure.

### Migration Safety & Rollback Runbook
1. **Zero-Downtime Migration Policy**:
   - All Flyway migration scripts must be backward-compatible (e.g., additive column additions, separate phases for column drops).
   - Before applying schema migrations in production, capture an on-demand snapshot:
     ```bash
     pg_dump -Fc -h <db_host> -U postgres -d gotham -f "/backup/pre_migration_$(date +%s).dump"
     ```
2. **Restoration Procedure**:
   - To restore from a logical dump:
     ```bash
     pg_restore -h <db_host> -U postgres -d gotham --clean --if-exists "/backup/gotham_backup.dump"
     ```
   - Verify table counts, constraints, and Flyway `flyway_schema_history` table consistency.

