# Company App — Architecture & Design Specification

## 1. Purpose

Living reference for all architectural decisions. When confused, refer here.
When things change, update here.

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
Kotlin Backend (Javalin) — hosted on Oracle Cloud (singapore west)
        ↓
PostgreSQL Database
```

Single language (Kotlin) across mobile, shared logic, and backend.

All branches connect to a single cloud-hosted backend. There is no per-branch
server. Each branch has its own inventory stock but shares the same database
and user accounts.

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Mobile UI | Kotlin Multiplatform + Compose Multiplatform |
| Backend | Kotlin (JVM), Javalin, HikariCP, Exposed (DSL), kotlin-logging |
| Database | PostgreSQL 15+, Flyway |
| Infrastructure | Docker, Docker Compose, Oracle Cloud Free Tier (singapore west), Coolify |
| Tooling | IntelliJ CE, Bruno/Postman, DBeaver |
| Quality | Detekt, Ktlint, SonarLint |

**Non-goals:** No Node/Express, no Spring Boot, no paid tooling, no web dashboard.

**PostgreSQL version note:** PostgreSQL 14+ is required for the partial predicate
on the `no_remittance_overlap` EXCLUDE constraint
(`WHERE status = 'SUBMITTED'`). Verify the Coolify-managed Postgres container
version before deployment.

---

## 5. Hosting & Deployment

### Cloud Provider: Oracle Cloud Free Tier (singapore west region)
- Always-free ARM VM (4 cores, 24GB RAM) — no credits, no expiry
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
├── composeApp/
│   └── src/
│       ├── androidMain/
│       ├── commonMain/         # Shared ViewModels, Compose UI
│       ├── commonTest/
│       ├── desktopMain/        # [PLANNED]
│       └── iosMain/
│
├── iosApp/
│   └── iosApp.xcodeproj/
│
├── shared/
│   └── src/commonMain/kotlin/com/companyb/companyapp/
│       ├── domain/             # Sealed classes, enums, capability codes
│       ├── dto/                # Serializable request/response objects
│       └── validation/         # Shared validation logic (future use)
│
├── backend/
│   └── src/main/
│       ├── kotlin/com/companyb/companyapp/
│       │   ├── api/
│       │   │   ├── mapping/    # Domain results → HTTP responses
│       │   │   └── routes/     # HTTP endpoints
│       │   ├── auth/           # JWT handling, rate limiting, deny list
│       │   ├── config/         # Javalin config (serialization mapper)
│       │   ├── database/       # HikariCP + Flyway + Exposed setup
│       │   ├── logging/        # Logback converters, logging extensions
│       │   ├── middleware/      # Capability check middleware
│       │   ├── repository/     # DB queries + domain models
│       │   │   └── model/      # Table definitions (Exposed) + data classes
│       │   ├── service/        # Business logic, engines, audit logging
│       │   │   ├── commission/ # Commission recalculation engine
│       │   │   ├── remittance/ # Remittance submission engine
│       │   │   └── delegate/   # Medical mission delegate hook
│       │   ├── scheduler/      # Scheduled tasks (next appointment alerts)
│       │   ├── utils/
│       │   └── Main.kt
│       └── resources/
│           ├── db/migration/   # Flyway SQL files (V1__, V2__, ...)
│           └── logback.xml
│
├── docs/
│   ├── design_specification.md
│   ├── architecture_implementation_plan.md
│   └── engine_specifications.md
│
├── docker/
│   └── docker-compose.yml
├── config/detekt/detekt.yml
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── .env
└── .env.example
```

### Module Dependencies

```
composeApp ──depends on──> shared
backend ─────depends on──> shared
iosApp ──────imports────> shared (as KMP framework)
```

---

## 7. Module Plugin Decisions

| Module | Plugin | Why |
|--------|--------|-----|
| `shared` | `kotlinMultiplatform` | Targets JVM (backend) + Android + iOS |
| `backend` | `kotlin("jvm")` | JVM-only; uses Javalin, JDBC |
| `composeApp` | `kotlinMultiplatform` | Targets Android + iOS with Compose |
| `iosApp` | N/A (Xcode) | Swift/SwiftUI wrapper |

Use `kotlinMultiplatform` only when a module targets multiple platforms.

**Current `shared` targets:** `jvm()`, `androidTarget()`, `iosArm64()`

---

## 8. Backend Layering

### Routes (`api/routes`)
- Define endpoints, parse requests, invoke capability middleware, call services,
  return responses
- No business logic, no SQL

### Middleware (`middleware/`)
- Capability check middleware: reads JWT → resolves `userId` → calls
  `CapabilityService.hasCapability(...)` → 403 on failure
- This is the only place HTTP concerns and auth intersect
- No business logic beyond the capability check itself

### Services (`service/`)
- Business rules, validation, transaction boundaries, audit logging
- Source of truth for: commission engine, remittance submission, delegate hook,
  session type computation
- No HTTP concerns, no raw SQL

### Repositories (`repository/`)
- DB queries, row-to-domain-model mapping
- No business logic, no HTTP

### Database (`database/`)
- HikariCP config, Flyway init, connection management

### Scheduler (`scheduler/`)
- Lightweight task runner for next-appointment alerts
- Runs at 07:00 Asia/Manila daily
- No business logic — delegates entirely to service layer

---

## 9. Database Migrations

Flyway SQL files live at `backend/src/main/resources/db/migration/`:

```
V1__full_schema.sql          — all tables, constraints, indexes, views, triggers
V2__seed_roles.sql           — role rows
V3__seed_capabilities.sql    — capability codes + role_capability assignments
```

Flyway runs automatically on startup via `DatabaseConfig.runMigrations()`.

**Migration rules:**
- Never edit a committed migration file — always add a new version
- Destructive changes (DROP, RENAME) get their own migration with a comment
  explaining why

---

## 10. Authentication & Authorization

### Auth: JWT

- Issued on login, attached to all subsequent requests as `Authorization: Bearer
  <token>`
- Expiry: 24 hours
- Secret: long random string in `.env` — never commit, losing it invalidates all
  sessions

### AuthZ: Capability-Based (not role-based at runtime)

**Rule:** Never check a role name in service logic. Always check a capability code.

Roles are predefined bundles of capabilities — useful for seeding and UI display
only. At runtime, the only query that matters is:

```sql
SELECT 1 FROM active_user_capabilities
WHERE user_id       = :userId
  AND capability_id = (SELECT id FROM capability WHERE code = :code)
  AND context_type  = :contextType
  AND context_id    = :contextId
LIMIT 1
```

If any row is found, the user has the capability.

### Inactive User Revocation

On user deactivation:
1. Set `app_user.status = 'INACTIVE'`
2. Add `userId` to in-memory deny list (evicted after 24h — the JWT max lifetime)
3. JWT middleware rejects all requests from deny-listed users before capability check

On server restart: repopulate deny list from `SELECT id FROM app_user WHERE status
= 'INACTIVE'`.

### Temporary/Scoped Grants

Relief access, medical mission delegation, and any future manual overrides are
stored in `user_capability` with `source_type`, `source_id`, `valid_from`,
`valid_to`, and `priority`. The `active_user_capabilities` view surfaces only
currently-valid grants. No role change is needed.

### Password Handling

- Plaintext over HTTPS (safe in transit via TLS)
- Server hashes with bcrypt (60-char output) before storing
- Login: `bcrypt.verify(plaintext, storedHash)`
- Never store or log plaintext passwords

### OWASP

- **A02:** bcrypt for passwords, strong random JWT secret
- **A07:** Identical error for wrong username vs wrong password (prevent
  enumeration), 24h JWT expiry, rate-limited login

---

## 11. Connectivity & Reliability

### Strictly Online

No offline queue. No local-first sync. No conflict resolution beyond optimistic
locking.

### Optimistic UI

Client shows the expected result immediately. A "Saving…" or "Retrying…" indicator
is shown until the server acknowledges. On definitive failure (4xx), the UI reverts.

### Idempotency (Insert Path)

Client generates a UUID for every new record before sending the request. The
server uses this UUID as the primary key. On duplicate insert (same UUID):
- Return the existing record as if the insert succeeded (200/201)
- Do not write a duplicate row

Covers: session, product_sale, inventory_movement, attendance (clock-in), expense,
compensation, allowance.

### Optimistic Locking (Update Path)

Three tables carry a `version INT NOT NULL DEFAULT 1`:

| Table | Risk |
|-------|------|
| `session` | Multiple practitioners adding themselves concurrently |
| `branch_inventory` | Sales racing against restocks |
| `remittance` | Two coordinators editing/submitting |

Every UPDATE sends `expected_version`. Server rejects with `409 Conflict` on
mismatch. Client re-fetches and retries.

### Authoritative Server Time

All timestamps are server-stamped. Clients never send "current time." The server
computes the 4 AM Manila boundary for relief access expiry and day-state
evaluation. Day-state transitions (OPEN → PAST) are lazy — evaluated on each
request, not via a cron job.

---

## 12. Day State Machine

Each `branch_day` has a status that governs edit permissions:

| Status | When | Who Can Edit |
|--------|------|--------------|
| `OPEN` | Current calendar day (until 04:00 AM Asia/Manila next day) | All on-duty users per capability |
| `PAST` | After 04:00 AM boundary, not yet remitted | Coordinator only (`EDIT_PAST_DAY` capability) |
| `REMITTED` | Covered by a submitted remittance | Coordinator only (`EDIT_PAST_DAY` capability) |

Transitions are lazy. The service layer computes the effective status from
`branch_day.date` and `branch_day.status` on each request. Remittance submission
writes `REMITTED` explicitly.

Edits to REMITTED records require a `reason` in the request body. The service
layer sets `is_flagged = true` on the audit log entry and rejects the request if
`reason` is absent.

---

## 13. Audit Logging

Records business events in the database. Written in the **service layer only** —
never in routes or repositories.

**Covered tables:** `session`, `session_void`, `product_sale`, `compensation`,
`commission_split`, `expense`, `remittance`, `remittance_line`,
`inventory_movement`, `attendance`, `branch_day`, `user_branch_assignment`.

**Fields:** `table_name`, `record_id`, `action` (INSERT/UPDATE/DELETE),
`changed_by`, `changed_at`, `old_value` (JSONB), `new_value` (JSONB),
`is_flagged`, `reason`, `acknowledged_by`, `acknowledged_at`.

**Flagging:** Edits on REMITTED days are automatically flagged. Owner and
Accountant acknowledge flagged entries via the alert dashboard.

### Logging vs Auditing

| | Application Logging | Audit Logging |
|--|---------------------|---------------|
| Nature | Technical | Business |
| Storage | Text/console | Database |
| Purpose | Debugging | Accountability |
| Lifespan | Temporary | Permanent |

---

## 14. Client Search

- `pg_trgm` extension with `ILIKE` for fuzzy name matching
- Handles typos ("Jhn" → "John")
- Typeahead: frontend debounces ~300ms before firing
- Supports both name (fuzzy) and phone number (exact prefix)
- No external search engine needed at this scale

---

## 15. Commission Engine

Full algorithm: `docs/engine_specifications.md` (Engine 1).

**Summary:** For each product sale, find users clocked in at `sold_at`, apply
manual overrides, split the sale's commission equally among eligible users. Accumulate
totals per user across all sales. Flush the entire batch to `commission_split` in
one upsert. Delete users who dropped out of eligibility entirely.

**Precision:** All arithmetic in `BigDecimal` (Kotlin). Never `Double`. Stored as
`NUMERIC(15,4)`. UI truncates to 2 decimal places for the cash handout display.

**Freeze policy:** Once `branch_day.status` transitions to `PAST` or `REMITTED`,
the split is frozen. Retroactive product sales do not trigger automatic
recalculation. Coordinator initiates a manual re-run if correction is needed.

---

## 16. Remittance

Two independent flows per branch: `SESSION` and `PRODUCT`.

Full submission algorithm: `docs/engine_specifications.md` (Engine 3).

**Draft:** Multiple coordinators can view and edit. No lock during draft. Last
write wins on draft line edits (acceptable — snapshot is taken only at submission).

**Submission:** `SERIALIZABLE` isolation, `SELECT FOR UPDATE` on remittance row,
version check, snapshot write, status update, branch_day transition — all atomic.

**Financial snapshot** (`remittance_financial_snapshot`): written once at
submission, immutable (trigger blocks UPDATE/DELETE). Only for `SESSION`
remittances. `PRODUCT` remittance totals derive from `remittance_line.amount`.

**Overlap constraint:** `EXCLUDE USING gist` scoped to `status = 'SUBMITTED'`.
Drafts are unconstrained — Coordinators can create, discard, and recreate freely.

---

## 17. Environment Configuration

Secrets loaded from `.env` via `dotenv-kotlin`. Never commit `.env`.

```
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=
APP_PORT=
JWT_SECRET=
JWT_ISSUER=
JWT_AUDIENCE=
TZ=Asia/Manila          # Server JVM timezone for consistent date arithmetic
```

---

## 18. Developer Setup

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

## 19. Development Phases

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

**2A — Foundation**
- Seed roles + capabilities (Flyway)
- `CapabilityService` + `active_user_capabilities` view wired into middleware
- JWT deny list for inactive users
- `BranchDayRepository.resolveOrCreate(branchId, date)`

**2B — Attendance + Auth**
- Clock-in / clock-out with `branch_day_assignment` creation
- Relief access request + grant + `user_capability` write
- Medical mission delegate assign/revoke + `user_capability` write

**2C — Sessions**
- Client CRUD + search
- Session create (type computation, concurrent guard, rate snapshot)
- Session status, void/un-void, practitioner management
- Concern promotion flow

**2D — Inventory**
- Product + category CRUD
- `branch_inventory` + `inventory_movement` with day-state enforcement
- Product sale (in-session, known client walk-in, anonymous walk-in)

**2E — Finance**
- Compensation assign
- Commission recalculation engine + manual inclusion
- Expense CRUD + allowance assign

**2F — Remittance**
- Draft creation + line management
- Submission (atomic, serializable, snapshot)
- Branch day REMITTED transition

**2G — Reporting**
- `daily_sales_summary` view
- `monthly_remittance_summary` view
- Export endpoints
- Next appointment alert scheduler

### Phase 3 — Client Applications
- Android login + main screens
- iOS app
- Shared ViewModels in `composeApp/src/commonMain`

### Phase 4 — Deployment
- Oracle Cloud VM provisioned
- Coolify installed and configured
- Backend deployed via Coolify from Git
- Let's Encrypt HTTPS active
- Server hardening complete

### Phase 5 — Enhancements
- Realtime updates (SSE or WebSockets)
- Audit visibility UI
- CI/CD pipeline
- Windows Desktop app

---

## 20. Guiding Principles

- Clarity over cleverness
- Thin, explicit layers — routes parse, services decide, repositories query
- Never check role names at runtime — check capability codes
- All timestamps are server-stamped — clients never send "current time"
- Financial arithmetic in BigDecimal — never Double
- Every mutating request is idempotent via client-generated UUID
- The audit log is the source of truth for change history — records are mutable,
  the log is not
