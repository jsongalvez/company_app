# Company App — Architecture & Design Specification

## 1. Purpose

Living reference for all architectural decisions. When confused, refer here. When things change, update here.

**Audience:** You (builder) + me (mentor/reviewer).

---

## 2. Problem Statement

Internal company app for employees to:
- Log in
- Enter business data
- View a shared dashboard of aggregated data

**Platforms:** Android + iOS
**Constraints:** Small team, free tools/hosting, clarity over speed.

---

## 3. High-Level Architecture

```
Mobile Clients (Android + iOS)
        ↓
Kotlin Multiplatform (composeApp/commonMain + shared module)
        ↓
Kotlin Backend (Javalin)
        ↓
PostgreSQL Database
```

> **Single language (Kotlin) across mobile, shared logic, and backend.**

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Mobile UI | Kotlin Multiplatform + Compose Multiplatform |
| Backend | Kotlin (JVM), Javalin, HikariCP, Exposed (DSL), kotlin-logging |
| Database | PostgreSQL, Flyway |
| Infrastructure | Docker, Docker Compose |
| Tooling | IntelliJ CE, Bruno/Postman, DBeaver |
| Quality | Detekt, Ktlint, SonarLint |

**Non-goals:** No Node/Express, no Spring Boot, no paid tooling, no web dashboard.

---

## 5. Repository Structure

```
company-app/
├── composeApp/                    # KMP mobile (keep wizard-generated structure)
│   └── src/
│       ├── androidMain/           # Android entry point, Android-specific UI
│       ├── commonMain/            # ViewModels, Compose UI (shared Android+iOS)
│       └── iosMain/               # iOS expect/actual
│
├── iosApp/                        # iOS entry point (keep wizard-generated)
│   └── iosApp.xcodeproj
│
├── shared/                        # Shared: mobile + backend
│   └── src/commonMain/kotlin/com/companyb/companyapp/
│       ├── dto/
│       ├── domain/
│       └── validation/
│
├── backend/                       # Javalin backend (JVM-only module)
│   └── src/main/kotlin/com/companyb/companyapp/
│       ├── api/routes/            # HTTP endpoints
│       ├── service/               # Business logic
│       ├── repository/            # Database access
│       │   └── model/             # DB table definitions + domain models (AppUserTable, AppUser)
│       ├── database/              # HikariCP + Flyway config
│       ├── auth/                  # JWT handling
│       ├── config/
│       └── Main.kt
│   └── src/main/resources/
│       └── db/migration/          # Flyway SQL files (V1__, V2__, ...)
│
├── docker/
│   └── docker-compose.yml
├── config/detekt/detekt.yml
├── .editorconfig
├── .env                           # Local only — never commit
├── .env.example                   # Committed template
└── settings.gradle.kts            # Includes :composeApp, :shared, :backend
```

### Module Dependencies

```
composeApp ──depends on──> shared
backend ─────depends on──> shared
iosApp ──────imports────> shared (as KMP framework)
```

### Key Principles

1. `composeApp` keeps wizard structure — don't rename or restructure
2. `shared` is pure logic — no UI, no HTTP, no SQL
3. `backend` depends only on `shared` — never on `composeApp` or `iosApp`
4. `iosApp` imports `shared` via KMP framework embedding

---

## 6. Module Plugin Decisions

| Module | Plugin | Why |
|--------|--------|-----|
| `shared` | `kotlinMultiplatform` | Targets JVM (backend) + Android + iOS |
| `backend` | `kotlin("jvm")` | JVM-only; uses Javalin, JDBC — no need for multiplatform |
| `composeApp` | `kotlinMultiplatform` | Targets Android + iOS with Compose |
| `iosApp` | N/A (Xcode) | Swift/SwiftUI wrapper |

> Use `kotlinMultiplatform` only when a module targets multiple platforms. Single-platform = simpler config.

**Current `shared` targets:** `jvm()`, `androidTarget()`, `iosArm64()`

---

## 7. Backend Layering

### Routes (`api/routes`)
- Define endpoints, parse requests, auth checks, call services, return responses
- ❌ No business logic, no SQL

### Services (`service`)
- Business rules, validation, permission checks, transaction boundaries, audit logging
- ❌ No HTTP concerns, no raw SQL

### Repositories (`repository`)
- DB queries, map rows to domain models
- ❌ No business logic, no HTTP

### Database (`database`)
- HikariCP config, Flyway init, connection management

---

## 8. Database Migrations

Flyway SQL files live at `backend/src/main/resources/db/migration/`:

```
V1__Create_User_Table.sql
V2__...
```

Flyway runs automatically on startup via `DatabaseConfig.runMigrations()`.

---

## 9. Audit Logging

Records **business events** (not app logs) in the database.

**Events:** login, entry created/updated/deleted

**Minimal table fields:** `user_id`, `action`, `timestamp`
**Optional later:** `entity_type`, `entity_id`, `old_value`/`new_value` (JSON)

**Rule:** Audit logging happens in the **service layer only** — never in routes or repositories.

### Logging vs Auditing

| | Logging | Auditing |
|--|---------|----------|
| Nature | Technical | Business |
| Storage | Text/console | Database |
| Purpose | Debugging | Accountability |
| Lifespan | Temporary | Permanent |

---

## 10. Authentication & Authorization

- **Auth:** JWT — issued on login, attached to subsequent requests
- **AuthZ:** Role-based — `ADMIN`, `MANAGER`, `EMPLOYEE`, `VIEWER`

### Password Handling

- Passwords are transmitted as plaintext over HTTPS (safe in transit via TLS)
- Server hashes using **bcrypt** (60-char output) before storing — never store plaintext
- Login comparison: `bcrypt.verify(plaintext, storedHash)` — never decrypt
- DB column: `password_hash CHAR(60)`

### Network Security

> **Decision (Mar 2026):** Deployment target is unknown — office environment, possibly public mall wifi or shared network. Assume worst-case (untrusted network).

- **HTTPS is required before any real user data is handled** — without it, login credentials are visible to anyone on the same network
- During development on localhost, HTTP is acceptable (traffic never leaves the device)
- Before production: configure Javalin with TLS (self-signed cert for LAN, Let's Encrypt if internet-facing)
- Reject plain HTTP requests in production

### OWASP Considerations

- **A02 Cryptographic Failures** — bcrypt for passwords, strong random JWT secret (never commit to git)
- **A07 Auth Failures** — return identical error for wrong username vs wrong password (prevent username enumeration), expire tokens (24hr JWT), rate-limit login attempts (Phase 4)

---

## 11. Environment Configuration

Secrets are loaded from `.env` using `dotenv-kotlin`. Never commit `.env` — use `.env.example` as the template.

Required vars:
```
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=
APP_PORT=
JWT_SECRET=       # Long random string — never commit, losing it invalidates all sessions
```

---

## 12. Developer Setup

```bash
# Start Postgres
docker compose -f docker/docker-compose.yml up -d

# Stop and wipe volumes
docker compose -f docker/docker-compose.yml down -v

# Lint
./gradlew ktlintCheck
./gradlew ktlintFormat
```

**One-time IDE setup:**
- Install Ktlint + Detekt plugins
- Detekt config: Settings → Tools → detekt → add `config/detekt/detekt.yml`

---

## 13. Development Phases

### Phase 1 — Backend Core ✅ In Progress
- Javalin + HikariCP + Flyway wired up
- Postgres via Docker
- JWT auth

### Phase 2 — Business Features
- Entry submission endpoints
- Dashboard aggregation APIs
- DTOs in `shared/src/commonMain`

### Phase 3 — Mobile
- Login screen in `composeApp/src/androidMain`
- SwiftUI in `iosApp/`
- Shared ViewModels in `composeApp/src/commonMain`

### Phase 4 — Enhancements
- Realtime updates (SSE or WebSockets)
- Audit visibility UI
- CI/CD

---

## 14. Guiding Principles

- Clarity over cleverness
- Thin, explicit layers
- One responsibility per module
- Build the smallest thing that works
- Learn by doing, not by optimizing early
