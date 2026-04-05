# Company App — Architecture & Design Specification

## 1. Purpose

Living reference for all architectural decisions. When confused, refer here. When things change, update here.

**Audience:** You (builder) + me (mentor/reviewer).

---

## 2. Problem Statement

Internal company app for employees to:
- Log in
- Enter business data (client sessions, inventory, finance)
- View a shared dashboard of aggregated data

**Platforms:** Android + iOS (Windows Desktop planned — later phase)
**Constraints:** Small team, free tools/hosting, clarity over speed.

---

## 3. High-Level Architecture

```
Clients (Android + iOS)
        ↓
    HTTPS (TLS)
        ↓
Kotlin Backend (Javalin) — hosted on Oracle Cloud (Osaka)
        ↓
PostgreSQL Database
```

> **Single language (Kotlin) across mobile, shared logic, and backend.**

All branches connect to a single cloud-hosted backend. There is no per-branch server.
Each branch has its own inventory stock but shares the same database and user accounts.

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Mobile UI | Kotlin Multiplatform + Compose Multiplatform |
| Backend | Kotlin (JVM), Javalin, HikariCP, Exposed (DSL), kotlin-logging |
| Database | PostgreSQL, Flyway |
| Infrastructure | Docker, Docker Compose, Oracle Cloud Free Tier (Osaka), Coolify |
| Tooling | IntelliJ CE, Bruno/Postman, DBeaver |
| Quality | Detekt, Ktlint, SonarLint |

**Non-goals:** No Node/Express, no Spring Boot, no paid tooling, no web dashboard.

---

## 5. Hosting & Deployment

### Cloud Provider: Oracle Cloud Free Tier (Osaka region)
- Always-free ARM VM (4 cores, 24GB RAM) — no credits, no expiry
- Osaka chosen over Tokyo/Seoul due to capacity availability warnings on free ARM instances
- Account type: Free Tier only — never upgrade to Pay As You Go

### Deployment Platform: Coolify
- Self-hosted PaaS installed on the Oracle VM
- Provides Railway-like experience: deploy from Git, manage env vars, automatic HTTPS
- Postgres runs as a Coolify-managed container on the same VM
- Let's Encrypt handles TLS automatically — no manual cert management

### Server Hardening (one-time setup)
- SSH key authentication only — password login disabled
- SSH on a non-standard port (not 22, not 2222 — pick something obscure e.g. 51920)
- `PermitRootLogin no`
- Oracle Security Groups + `ufw` as two independent firewall layers
    - Open ports: 443 (HTTPS), 80 (cert renewal), custom SSH port only
- Fail2ban on SSH
- Unattended security upgrades enabled

### Network
- All branches use the internet (mobile hotspots per employee)
- No Tailscale or VPN needed — backend is publicly reachable over HTTPS
- Branches are independent units sharing one backend and one database

---

## 6. Repository Structure

```
company-app/
├── composeApp/                    # KMP mobile + desktop (keep wizard-generated structure)
│   └── src/
│       ├── androidMain/           # Android entry point, Android-specific UI
│       ├── commonMain/            # Shared ViewModels, Compose UI (Android + iOS + Desktop)
│       ├── commonTest/            # Shared tests
│       ├── desktopMain/           # [PLANNED] Windows Desktop entry point
│       └── iosMain/               # iOS expect/actual implementations
│
├── iosApp/                        # iOS entry point (keep wizard-generated)
│   └── iosApp.xcodeproj/
│
├── shared/                        # Shared logic: mobile + backend
│   └── src/commonMain/kotlin/com/companyb/companyapp/
│       ├── domain/                # Sealed classes, enums (RegisterResult, ErrorCode)
│       ├── dto/                   # Serializable request/response objects
│       └── validation/            # Shared validation logic (future use)
│
├── backend/                       # Javalin backend (JVM-only module)
│   └── src/main/
│       ├── kotlin/com/companyb/companyapp/
│       │   ├── api/
│       │   │   ├── mapping/       # Map domain results to HTTP responses
│       │   │   └── routes/        # HTTP endpoints
│       │   ├── auth/              # JWT handling, rate limiting
│       │   ├── config/            # Javalin config (serialization mapper)
│       │   ├── database/          # HikariCP + Flyway + Exposed setup
│       │   ├── logging/           # Logback converters, logging extensions
│       │   ├── middleware/        # [PLANNED] RBAC role-check middleware
│       │   ├── repository/        # DB queries + domain models
│       │   │   └── model/         # Table definitions (Exposed) + data classes
│       │   ├── service/           # Business logic
│       │   ├── utils/             # Misc helpers
│       │   └── Main.kt
│       └── resources/
│           ├── db/migration/      # Flyway SQL files (V1__, V2__, ...)
│           └── logback.xml
│
├── docs/
│   └── design_specification.md   # This file
│
├── docker/
│   └── docker-compose.yml        # Local dev only
├── config/detekt/detekt.yml
├── gradle/libs.versions.toml     # All dependency versions in one place
├── settings.gradle.kts           # Includes :composeApp, :shared, :backend
├── .env                          # Local only — never commit
└── .env.example                  # Committed template
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

## 7. Module Plugin Decisions

| Module | Plugin | Why |
|--------|--------|-----|
| `shared` | `kotlinMultiplatform` | Targets JVM (backend) + Android + iOS |
| `backend` | `kotlin("jvm")` | JVM-only; uses Javalin, JDBC — no need for multiplatform |
| `composeApp` | `kotlinMultiplatform` | Targets Android + iOS with Compose |
| `iosApp` | N/A (Xcode) | Swift/SwiftUI wrapper |

> Use `kotlinMultiplatform` only when a module targets multiple platforms. Single-platform = simpler config.

**Current `shared` targets:** `jvm()`, `androidTarget()`, `iosArm64()`

---

## 8. Backend Layering

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

## 9. Database Migrations

Flyway SQL files live at `backend/src/main/resources/db/migration/`:

```
V1__Create_User_Table.sql
V2__...
```

Flyway runs automatically on startup via `DatabaseConfig.runMigrations()`.

---

## 10. Audit Logging

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

## 11. Authentication & Authorization

- **Auth:** JWT — issued on login, attached to subsequent requests
- **AuthZ:** Role-based access control (RBAC) — simpler and sufficient for this domain

### Roles
| Role | Who | Access |
|------|-----|--------|
| `ADMIN` | Owner/dad | Full access, manage employees |
| `MANAGER` | Branch manager | Finance, remittance, delegate remittance |
| `EMPLOYEE` | Practitioners | Log sessions, view clients, view/edit inventory |
| `VIEWER` | Read-only | Future use |

> ABAC was considered but rejected — permission questions in this domain are role-level, not attribute-level. RBAC is sufficient and far simpler.

Role is stored as a column on `app_user`. Role checks are enforced in the service layer via middleware.

### Password Handling

- Passwords are transmitted as plaintext over HTTPS (safe in transit via TLS)
- Server hashes using **bcrypt** (60-char output) before storing — never store plaintext
- Login comparison: `bcrypt.verify(plaintext, storedHash)` — never decrypt
- DB column: `password_hash VARCHAR(60)`

### Network Security

> **Decision (Apr 2026):** All branches use mobile hotspots per employee. Backend is cloud-hosted and reachable over the public internet. HTTPS is always required.

- HTTPS enforced in production via Coolify + Let's Encrypt
- During development on localhost, HTTP is acceptable (traffic never leaves the device)
- Reject plain HTTP requests in production

### OWASP Considerations

- **A02 Cryptographic Failures** — bcrypt for passwords, strong random JWT secret (never commit to git)
- **A07 Auth Failures** — return identical error for wrong username vs wrong password (prevent username enumeration), expire tokens (24hr JWT), rate-limit login attempts (implemented)

---

## 12. Client Search

- PostgreSQL `pg_trgm` extension with `ILIKE` for fuzzy name matching
- Handles typos (e.g. "Jhn" finds "John")
- Typeahead UX: frontend debounces ~300ms before firing search request
- No external search engine needed at this scale

---

## 13. Business Domain (Phase 2 target)

> Some details pending confirmation with dad. Marked where assumptions were made.

### Core Entities

**Client** — has many Sessions
**Session** — one visit (walk-in or booked, treated identically once started)
**Product** — inventory item, stock is per-branch
**Branch** — physical location, each has its own inventory
**Employee (AppUser)** — practitioner or admin, belongs to a branch
**Remittance** — employee withdrawal of owed compensation

### Session fields
- date
- client name + cellphone
- concern/illness
- session number (1st, 2nd, etc.)
- next appointment date
- practitioner
- payment for service
- products bought (with price per item)

### Pending questions for dad
- Is a client shared across branches, or per-branch?
- Next appointment alerts — who gets notified, how (in-app only)?
- Practitioner compensation — fixed salary, per-session, or both?
- Remittance — tracked per transaction or running balance?
- Who can register new employees — admin only?
- Monthly summary — what exactly is shown?

---

## 14. Environment Configuration

Secrets are loaded from `.env` using `dotenv-kotlin`. Never commit `.env` — use `.env.example` as the template.

Required vars:
```
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=
APP_PORT=
JWT_SECRET=       # Long random string — never commit, losing it invalidates all sessions
JWT_ISSUER=
JWT_AUDIENCE=
```

---

## 15. Developer Setup

```bash
# Start Postgres locally
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

## 16. Development Phases

### Phase 1 — Backend Core ✅ Complete
- Javalin + HikariCP + Flyway wired up
- Postgres via Docker
- JWT auth (issuer, audience, expiry, clock skew)
- Login + Register endpoints
- Rate limiting on login
- bcrypt password hashing
- UUID masking in logs
- Request tracing (traceId, elapsed time, delta time)

### Phase 2 — Business Features (current)
- RBAC: add `role` + `branch_id` to `app_user`
- Schema: Client, Session, Product, Branch, Remittance (Flyway V2+)
- Client search with pg_trgm
- Session CRUD endpoints
- Inventory endpoints (per-branch stock)
- Finance/remittance endpoints
- Daily sales view
- DTOs in `shared/src/commonMain`

### Phase 3 — Client Applications
- Android login + main screens (`composeApp/src/androidMain`)
- iOS app (`iosApp/`)
- Shared ViewModels in `composeApp/src/commonMain`

### Phase 4 — Deployment
- Oracle Cloud VM provisioned (Osaka, free ARM tier)
- Coolify installed and configured
- Backend deployed via Coolify from Git
- Let's Encrypt HTTPS active
- Server hardening complete (SSH keys, firewall, fail2ban)

### Phase 5 — Enhancements
- Next appointment alerts
- Monthly summary reports
- Realtime updates (SSE or WebSockets)
- Audit visibility UI
- CI/CD pipeline
- Windows Desktop app (`composeApp/src/desktopMain`) — when infrastructure is ready

---

## 17. Guiding Principles

- Clarity over cleverness
- Thin, explicit layers
- One responsibility per module
- Build the smallest thing that works
- Learn by doing, not by optimizing early
