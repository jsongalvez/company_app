# Architecture

## 1. Purpose

Living reference for all architectural decisions. When confused, refer here. When things change, update here.

**Audience:** AI agents and developers working on this codebase.

---

## 2. Problem Statement

Internal operations app for a multi-branch physical therapy practice. Practitioners log sessions, coordinators handle finance and remittance, owners manage users and branches.

All branches share one cloud-hosted backend and one database. Each branch maintains its own inventory stock.

**Platforms:** Android + iOS + Desktop (Windows planned)
**Constraints:** Small team, free tools/hosting, clarity over speed.

---

## 3. High-Level Architecture

```
Clients (Android + iOS + Desktop)
        ↓
    HTTPS (TLS)
        ↓
Kotlin Backend (Javalin) — hosted on Oracle Cloud (singapore west)
        ↓
PostgreSQL Database
```

Single language (Kotlin) across mobile, shared logic, and backend. All branches connect to a single cloud-hosted backend. There is no per-branch server.

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Mobile/Desktop UI | Kotlin Multiplatform + Compose Multiplatform |
| Backend | Kotlin (JVM), Javalin 7.2.2, HikariCP, Exposed DSL, kotlin-logging |
| Database | PostgreSQL 15+, Flyway |
| Infrastructure | Docker, Docker Compose, Oracle Cloud Free Tier (singapore west), Coolify |
| Quality | Detekt, Ktlint |
| Testing | JavalinTest, k6, JMH |

**Non-goals:** No Node/Express, no Spring Boot, no paid tooling, no web dashboard.

---

## 5. Hosting & Deployment

### Cloud Provider: Oracle Cloud Free Tier (singapore west)
- Always-free ARM VM (4 cores, 24GB RAM)
- Account type: Free Tier only — never upgrade to Pay As You Go

### Deployment Platform: Coolify
- Self-hosted PaaS installed on the Oracle VM
- Postgres runs as a Coolify-managed container on the same VM
- Let's Encrypt handles TLS automatically

### Server Hardening
- SSH key authentication only — password login disabled
- SSH on a non-standard port (e.g. 51920) — **superseded (session 65): the VPS-migration wizard hardens SSH to tailnet-only (no public SSH port)**
- `PermitRootLogin no`
- Oracle Security Groups + `ufw` as two independent firewall layers
- Fail2ban on SSH — **superseded (session 65): not needed with tailnet-only SSH**
- Unattended security upgrades enabled

### Network
- All branches use the internet (mobile hotspots per employee)
- No VPN needed — backend is publicly reachable over HTTPS

---

## 6. Repository Structure

```
company-app/
├── composeApp/
│   └── src/commonMain/       # Shared ViewModels, Compose UI, expect/actual Log
├── shared/
│   └── src/commonMain/       # Domain types, DTOs, validation, route constants
├── backend/
│   └── src/main/kotlin/com/companyb/companyapp/
│       ├── api/routes/       # HTTP endpoints
│       ├── api/middleware/    # Capability enforcement filters
│       ├── api/mapping/      # Domain results → HTTP responses
│       ├── auth/             # JWT handling, rate limiting, deny list
│       ├── config/           # Javalin config, serialization mapper
│       ├── database/         # HikariCP + Flyway + Exposed setup
│       ├── logging/          # Logback converters, logging extensions
│       ├── repository/       # DB queries + Exposed Table objects
│       │   └── model/        # Table definitions + data classes
│       ├── service/          # Business logic, engines
│       │   └── export/       # CSV + PDF export (deep module pattern)
│       ├── exception/        # Domain exception hierarchy
│       └── Main.kt
├── docs/
│   ├── architecture.md       # This file
│   ├── business-requirements.md
│   ├── engines.md
│   ├── agents/               # Agent meta-docs (issue tracker, triage, domain)
│   ├── adr/                  # Architecture decision records
│   └── specs/                # Feature specs
├── tests/k6/                 # HTTP-level load testing
└── docker/
```

### Module Dependencies

```
composeApp ──depends on──> shared
backend ─────depends on──> shared
iosApp ──────imports────> shared (as KMP framework)
```

- `shared` targets: JVM, Android, iOS
- `backend` is JVM-only (Javalin, JDBC)
- `composeApp` targets Android + iOS + Desktop with Compose

---

## 7. Backend Layering

### Routes (`api/routes`)
- Define endpoints, parse requests, invoke capability middleware, call services, return responses
- No business logic, no SQL

### Services (`service/`)
- Business rules, validation, transaction boundaries, audit logging
- No HTTP concerns, no raw SQL
- Source of truth for: commission engine, remittance submission, delegate hook, session type computation

### Repositories (`repository/`)
- DB queries, row-to-domain-model mapping
- Exposed DSL only — no raw SQL
- No business logic, no HTTP

### Auth (`auth/`)
- JWT issuance and verification, bcrypt password hashing, rate limiting, deny list
- Middleware reads JWT → resolves userId for subsequent capability checks

### Database (`database/`)
- HikariCP config, Flyway initialization, connection management

---

## 8. Deep Module Map

Modules are packages with a narrow public interface hiding significant implementation complexity. Use this as the reference when deciding where new logic belongs.

```
┌─────────────────────────────────────────────────────────────┐
│                         ROUTES LAYER                        │
│   (thin — parse request, call one service method, return)   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      CAPABILITY MIDDLEWARE                   │
│  hasCapability(userId, code, contextType, contextId): Bool  │
│  • queries active_user_capabilities view                    │
│  • checks JWT deny list                                     │
│  • one call — 403 or pass                                   │
└────────────────────────────┬────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
│  SESSION MODULE │ │ INVENTORY MODULE│ │  FINANCE MODULE │
│                 │ │                 │ │                 │
│ create()        │ │ recordMovement()│ │ assignCompensation()│
│ updateStatus()  │ │ getStock()      │ │ recalcCommission()  │
│ void()          │ │ getAlerts()     │ │ createDraft()       │
│ unvoid()        │ │                 │ │ submitRemittance()  │
│ addPractitioner │ │ Hides:          │ │ logExpense()        │
│ promoteConcern()│ │ • optimistic    │ │                     │
│                 │ │   lock on       │ │ Hides:              │
│ Hides:          │ │   branch_inv    │ │ • commission engine │
│ • session type  │ │ • sign/notes    │ │ • SERIALIZABLE      │
│   computation   │ │   constraints   │ │   submit tx         │
│ • concurrent    │ │ • branch_day    │ │ • snapshot write    │
│   session guard │ │   state check   │ │ • day transition    │
│ • base rate     │ │ • movement log  │ │ • BigDecimal        │
│   snapshot      │ │                 │ │   arithmetic        │
│ • void view     │ │                 │ │                     │
│   (never raw    │ │                 │ │                     │
│   session_void) │ │                 │ │                     │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
┌────────▼───────────────────▼───────────────────▼────────┐
│                     BRANCH DAY MODULE                    │
│             resolveOrCreate(branchId, date): BranchDay  │
│             assertEditable(branchDayId, userId)         │
│                                                         │
│  Hides: UPSERT branch_day, day-state evaluation,        │
│  OPEN/PAST/REMITTED logic, flagged audit enforcement    │
└──────────────────────────────┬──────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
┌────────▼────────┐  ┌─────────▼────────┐  ┌────────▼────────┐
│ ATTENDANCE MOD  │  │   AUTH MODULE    │  │  AUDIT MODULE   │
│                 │  │                  │  │                 │
│ clockIn()       │  │ login()          │  │ log(event)      │
│ clockOut()      │  │ issueToken()     │  │ flag(id, reason)│
│ markPresent()   │  │ validateToken()  │  │ acknowledge()   │
│                 │  │ deactivateUser() │  │                 │
│ Hides:          │  │                  │  │ Hides:          │
│ • branch_day_   │  │ Hides:           │  │ • old/new value │
│   assignment    │  │ • deny list      │  │   serialization │
│   creation      │  │ • bcrypt compare │  │ • flagging rule │
│ • is_relief     │  │ • JWT claims     │  │   (REMITTED     │
│   computation   │  │ • capability     │  │   days auto-    │
│ • multiple-     │  │   seeding        │  │   flag)         │
│   shift index   │  │                  │  │                 │
└────────┬────────┘  └────────┬─────────┘  └────────┬────────┘
         │                    │                      │
         └────────────────────┼──────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────┐
│                     REPOSITORY LAYER                     │
│   (one repo per aggregate — no cross-repo calls here)    │
│                                                          │
│  SessionRepo    InventoryRepo    RemittanceRepo          │
│  ClientRepo     CompensationRepo AttendanceRepo          │
│  BranchDayRepo  UserRepo         AuditRepo               │
└──────────────────────────────────────────────────────────┘
```

### Where depth lives

**BranchDayModule** is the deepest single module. Every financial or operational write calls `resolveOrCreate` and `assertEditable` first. All day-state logic lives here and nowhere else.

**FinanceModule** is the second deepest. The commission engine, serializable remittance submission, snapshot write, and BigDecimal arithmetic are all hidden behind four method calls.

**CapabilityMiddleware** is narrow by design — one boolean return — but hides the view query, time-window filtering, deny list check, and priority resolution.

**AuditModule** looks trivial from the outside (`log(event)`) but hides old/new value diffing, flagging rules, and JSONB serialization.

**Repository layer** is intentionally shallow. Interface complexity roughly matches implementation complexity. Repos are not candidates for deepening.

---

## 9. Auth & Authorization

### 9.1 JWT Authentication

- Issued on login, attached to all subsequent requests as `Authorization: Bearer <token>`
- Expiry: 24 hours
- Secret: long random string in `.env` — never commit

### 9.2 Capability-Based Authorization

**Rule:** Never check a role name in service logic. Always check a capability code.

Roles are predefined bundles of capabilities — useful for seeding and UI display only. At runtime, the only query that matters is:

```sql
SELECT 1 FROM active_user_capabilities
WHERE user_id = :userId AND capability_id = (SELECT id FROM capability WHERE code = :code)
  AND context_type = :contextType AND context_id = :contextId
LIMIT 1
```

The service layer exposes one method:

```kotlin
fun hasCapability(
    userId: UUID,
    capabilityCode: String,
    contextType: CapabilityContextType,
    contextId: UUID,
): Boolean
```

Called at the top of every mutating service method — not in routes, not in repositories.

### 9.3 Capability Codes

All capability codes are inserted as seed data in `V2__seed_capabilities.sql`. They are constants — not dynamic. The application references them by their string code, defined in `shared/domain/`.

| Code | Scope | Who holds it |
|------|-------|--------------|
| `VIEW_BRANCH_DATA` | BRANCH | Coordinator, Owner, Practitioner, Accountant |
| `EDIT_BRANCH_DATA` | BRANCH / BRANCH_DAY | Coordinator, Owner (OPEN days only), relief users |
| `VOID_SESSION` | BRANCH | Coordinator |
| `SUBMIT_REMITTANCE` | BRANCH | Coordinator |
| `ASSIGN_COMPENSATION` | BRANCH | Coordinator, Owner |
| `MANAGE_USERS` | GLOBAL | Owner, Manager |
| `MANAGE_PRODUCTS` | BRANCH | Owner (home branch only), Coordinator (assigned branches) |
| `ASSIGN_DELEGATE` | GLOBAL | Owner, Manager |
| `EDIT_PAST_DAY` | BRANCH | Coordinator only (PAST/REMITTED days) |

**Rule:** Owner does NOT hold `EDIT_PAST_DAY`. The branch state machine enforces Coordinator-only editing on PAST/REMITTED days.

### 9.4 context_type Enum Usage

`MEDICAL_MISSION` and `PROVINCIAL_TOUR` values in `capability_context_type` are reserved for future differentiation only. All current capability grants use `BRANCH` with the relevant `branch.id` as `context_id`.

### 9.5 Medical Mission Delegate Hook

Implemented as a service-layer hook, not a trigger. Both the delegate record and the capability row are written in a single transaction.

```
assignDelegate(targetUserId, branchId, assignedById):
  BEGIN
    INSERT medical_mission_delegate → delegate_id
    INSERT user_capability (
      source_type = MEDICAL_MISSION_DELEGATE,
      source_id   = delegate_id,
      context_type = BRANCH,
      context_id   = branchId,
      priority    = 20,
      valid_to    = NULL
    )
  COMMIT
```

The `active_user_capabilities` view's time-window filter (`now() <= valid_to`) handles expiry. No separate revocation step needed.

### 9.6 Inactive User Revocation

`active_user_capabilities` joins `app_user` and filters `status = 'ACTIVE'`. Setting a user to `INACTIVE` immediately revokes all capability checks. The session token itself is also rejected via an in-memory deny list keyed by `userId`.

**Deny list:** `ConcurrentHashMap<UUID, Instant>` — evicted after 24h (JWT max expiry). On server restart, repopulates from `app_user WHERE status = 'INACTIVE'`.

### 9.7 Relief Access Grant Flow

1. Relief user clocks into a non-home branch → `branch_day_assignment` row with `is_relief = true`. View-only access is implicit.
2. Relief user selects a target checked-in user → `grant_relief_access` row with status `PENDING`.
3. Target user grants → status `GRANTED`. Service layer writes `user_capability` row:
   ```
   source_type = RELIEF_ACCESS, context_type = BRANCH_DAY
   valid_to = (branch_day.date + 1 day) at 04:00 Asia/Manila
   priority = 10
   ```
4. Partial unique index `idx_one_grant_per_day` prevents a second GRANTED row for the same `(requested_by, branch_day_id)`.
5. Access expires naturally via `valid_to`. No cleanup job needed.

---

## 10. Database Migrations

Flyway SQL files live at `backend/src/main/resources/db/migration/`. Flyway runs automatically on startup via `DatabaseConfig.runMigrations()`.

**Migration rules:**
- Never edit a committed migration file — always add a new version
- Destructive changes (DROP, RENAME) get their own migration with a comment explaining why

Current migration files:
- `V1__full_schema.sql` — all tables, constraints, indexes, views, triggers
- `V2__seed_roles_capabilities.sql` — roles, capabilities, role_capability assignments

---

## 11. Day State Machine

Each `branch_day` has a status that governs edit permissions:

| Status | When | Who Can Edit |
|--------|------|--------------|
| `OPEN` | Current calendar day (until 04:00 AM Asia/Manila next day) | All on-duty users per capability |
| `PAST` | After 04:00 AM boundary, not yet remitted | Coordinator only (`EDIT_PAST_DAY` capability) |
| `REMITTED` | Covered by a submitted remittance | Coordinator only (`EDIT_PAST_DAY` capability) |

Transitions are lazy. The service layer computes the effective status from `branch_day.date` and `branch_day.status` on each request. Remittance submission writes `REMITTED` explicitly.

Edits to REMITTED records require a `reason` in the request body. The service layer sets `is_flagged = true` on the audit log entry and rejects the request if `reason` is absent.

---

## 12. Audit Logging

### 12.1 Rules

- Audit entries are written in the **service layer only** — never in routes, never in repositories
- Every INSERT, UPDATE, and soft-DELETE to financial and operational tables gets an audit entry
- Written inside the same DB transaction as the mutation (via callback pattern, see ADR 0013)
- The `AuditLogRepository` convenience methods (`recordInsert`, `recordUpdate`, `recordDelete`) accept `Map<String, String>` field maps
- Each Table companion defines an `auditFields(entity)` function (see ADR 0014)

**Covered tables:** `session`, `session_void`, `product_sale`, `compensation`, `commission_split`, `expense`, `remittance`, `remittance_line`, `inventory_movement`, `attendance`, `branch_day`, `user_branch_assignment`.

### 12.2 Flagging Policy

Any write to a record that belongs to a REMITTED branch day:
- Sets `is_flagged = true` on the audit entry
- Requires a non-null `reason` field (enforced at the service layer)
- Surfaces in the Coordinator alert dashboard: `WHERE is_flagged = true AND acknowledged_at IS NULL`

---

## 13. Attendance

### 13.1 Clock-In / Clock-Out Model

`attendance` supports multiple rows per `(user_id, branch_day_id)` for multiple shifts. The partial unique index `idx_one_active_clock_in WHERE clock_out IS NULL` enforces only one open window at a time.

The service layer must create or verify a `branch_day_assignment` row before inserting the first `attendance` row. `is_relief` is computed by checking `user_branch_assignment WHERE user_id = ? AND branch_id = ? AND ended_at IS NULL`. If no active home assignment exists, `is_relief = true`.

### 13.2 Commission Eligibility Window

The commission engine determines eligibility per sale based on the `sold_at` timestamp intersecting any open attendance window for that user on that branch day. Multiple shifts are correctly handled: a user who clocked out for lunch and clocked back in is eligible for sales within either window.

### 13.3 No attendance_history Table

Historical reads use `attendance` directly. The audit log captures all edits. Monthly summary queries `attendance JOIN branch_day` for who-was-present reports.

---

## 14. Sessions

### 14.1 Session Type Assignment

Session type is computed at creation time by the service layer and stored. Never recomputed. Never manually changed.

```
fun computeSessionType(branchType, priorCount):
  if branchType == MEDICAL_MISSION → MEDICAL_MISSION
  if branchType == PROVINCIAL_TOUR && priorCount == 0 → PROVINCIAL_FIRST
  if priorCount == 0  → REGULAR
  if priorCount == 1  → SECOND_SESSION
  else                → SUBSEQUENT
```

"Non-voided" means: session has no row in `active_session_voids` (the view, never raw `session_void`).

**The void/unvoid edge case:** If a session is voided after later sessions were created, those later sessions retain their computed type. The type is a snapshot at creation time.

### 14.2 Concurrent Session Guard

Only one PENDING session per client globally. Enforced by a partial unique index on `session (client_id)` scoped to PENDING rows, with a row-level lock on the client before insert to serialize concurrent creation.

### 14.3 Session Base Rate: No Gap/Overlap

The `no_rate_overlap` EXCLUDE constraint on `branch_id, session_type, tstzrange(effective_from, effective_until)` prevents overlapping rate periods.

When a Coordinator updates a rate:
1. Set `effective_until` on the current active rate to `now()` (Manila time → UTC)
2. Insert a new rate row with `effective_from = now()`

### 14.4 Concern Promotion Flow

When a Coordinator promotes an "other_concerns" string to a structured concern:
1. `INSERT INTO concern (label, created_by, created_at)`
2. `INSERT INTO session_concern (session_id, concern_id)`
3. `UPDATE session SET other_concerns = NULL`
4. Audit log entry for the session UPDATE

### 14.5 Next Appointment Alerts

Server-side scheduled task runs once daily at 07:00 Asia/Manila. Finds sessions with `next_appointment_date = CURRENT_DATE + 2 days` and creates notification rows for the branch's Coordinators.

### 14.6 Client Search

- `pg_trgm` extension with `ILIKE` for fuzzy name matching — handles typos ("Jhn" → "John")
- Typeahead: frontend debounces ~300ms before firing
- Search supports both name (fuzzy) and phone number (exact prefix)
- No external search engine needed at this scale

---

## 15. Inventory

### 15.1 Movement and branch_day_id

Every `inventory_movement` row carries `branch_day_id`. The service creates the `branch_day` row before inserting the movement.

Day-state enforcement:
- `OPEN` → any authorised user can insert
- `PAST` → Coordinator only (`EDIT_PAST_DAY`)
- `REMITTED` → Coordinator only, flagged audit entry required

### 15.2 Stock Integrity: Optimistic Lock on branch_inventory

`branch_inventory` carries a `version INT NOT NULL DEFAULT 1`. Every stock-modifying operation checks the version. If 0 rows updated → version mismatch → 409 Conflict.

For sale operations, the stock update and the product_sale + inventory_movement inserts happen in the same transaction. A guard check prevents stock going below zero.

### 15.3 Sign and Notes Constraints (DB-enforced)

- `RESTOCK` → `quantity_change > 0`
- `SALE, TESTER, SAMPLE, MISSING` → `quantity_change < 0`
- `ADJUSTMENT` → either sign
- `MISSING` → `notes IS NOT NULL AND length(notes) > 0`

---

## 16. Finance

### 16.1 Commission Recalculation Engine

Full algorithm in `docs/engines.md` (Engine 1). Triggered by product sale create, attendance clock-in/out, or manual inclusion changes. PAST/REMITTED days block automatic recalculation unless the user holds `EDIT_PAST_DAY` and initiates a manual re-run.

**Precision:** All intermediate arithmetic in `BigDecimal`. Stored as `NUMERIC(15,4)`.

### 16.2 Manual Commission Inclusion/Exclusion

Per-sale overrides stored in `commission_manual_inclusion` can force-add or force-remove a user from the eligible set for a specific product sale, regardless of attendance windows.

### 16.3 Compensation Uniqueness

One compensation payout per user per paying branch per day. `UNIQUE INDEX idx_compensation_unique ON compensation (user_id, paying_branch_day_id)`.

For relief duty, `work_branch_day_id ≠ paying_branch_day_id` is allowed and expected.

### 16.4 Remittance Submission

Full algorithm in `docs/engines.md` (Engine 3). `SERIALIZABLE` isolation prevents double-submission; optimistic locking on the `remittance` row detects concurrent draft edits. The snapshot, status update, and branch_day transitions are all written atomically in a single transaction.

**Exclusion constraint scope:** The `no_remittance_overlap` constraint applies only to `status = 'SUBMITTED'` rows. Drafts are unconstrained.

### 16.5 Remittance Financial Snapshot Immutability

A database trigger blocks UPDATE/DELETE on `remittance_financial_snapshot`. The snapshot is only written during submission — never pre-inserted.

For `PRODUCT` remittances: no snapshot row is written. Totals are derived at query time from `remittance_line.amount`.

---

## 17. Connectivity & Reliability

### 17.1 Strictly Online Model

No offline queue, no local-first storage, no sync conflict resolution. The client shows optimistic UI, retries on connection failure, and reverts to error state only on definitive server rejection (4xx).

### 17.2 Idempotency (Insert Path)

Every mutating INSERT uses a client-generated UUID as the primary key. On duplicate PK (`UNIQUE VIOLATION`), the existing row is returned as if the insert succeeded.

Covers: session, product_sale, inventory_movement, attendance clock-in, expense, compensation, allowance.

### 17.3 Optimistic Locking (Update Path)

| Table | Reason |
|-------|--------|
| `session` | Multiple practitioners adding themselves concurrently |
| `branch_inventory` | Sales racing against restocks |
| `remittance` | Two coordinators editing/submitting |

All three carry `version INT NOT NULL DEFAULT 1`. On mismatch → 409 Conflict. Client re-fetches and retries.

Other mutable tables (`expense`, `compensation`, `allowance`, etc.) are last-write-wins.

### 17.4 Authoritative Server Time

All timestamps are stamped server-side. The client never sends a "current time" — it sends intent. The server records `now()` (UTC, stored as `TIMESTAMPTZ`).

**4 AM boundary:** The server computes `valid_to` for relief access and day-state transitions using `Asia/Manila` timezone. Day-state transitions (OPEN → PAST) are lazy — evaluated on each request, not via a cron job.

---

## 18. Environment Configuration

Secrets loaded from `.env` via `dotenv-kotlin`. Never commit `.env`.

```
POSTGRES_DB=, POSTGRES_USER=, POSTGRES_PASSWORD=
APP_PORT=, JWT_SECRET=, JWT_ISSUER=, JWT_AUDIENCE=
TZ=Asia/Manila
```

---

## 19. Developer Setup

```bash
# Start Postgres locally
docker compose -f docker/docker-compose.yml up -d

# Stop and wipe volumes
docker compose -f docker/docker-compose.yml down -v

# Quality gate
./gradlew :backend:detekt :backend:ktlintCheck :backend:test

# Run backend
./gradlew :backend:run
```

---

## 20. Development Phases

### Phase 1 — Backend Core ✅ Complete
Javalin + HikariCP + Flyway wired up. JWT auth, login/register, rate limiting, bcrypt, UUID masking, request tracing.

### Phase 2 — Business Features (scoped below)

**2A — Foundation:** Seed roles + capabilities, `CapabilityService` + `active_user_capabilities` view, JWT deny list, `BranchDayRepository.resolveOrCreate`.

**2B — Attendance + Auth:** Clock-in/out, relief access grant flow, medical mission delegate assign/revoke.

**2C — Sessions:** Client CRUD + search, session create (type computation, concurrent guard, rate snapshot), status updates, void/unvoid, practitioner management, concern promotion.

**2D — Inventory:** Product + category CRUD, branch_inventory management + inventory_movement, product sale (in-session, known client walk-in, anonymous walk-in).

**2E — Finance:** Compensation assign, commission recalculation engine, manual inclusion, expense CRUD, allowance assign.

**2F — Remittance:** Draft creation + line management, submission (atomic, serializable, snapshot), branch day REMITTED transition.

**2G — Reporting:** Daily/monthly/all-time summary views, export endpoints, next appointment alert scheduler.

### Phase 3 — Client Applications
### Phase 4 — Deployment
### Phase 5 — Enhancements

---

## 21. Resolved Decisions

| # | Item | Decision |
|---|------|----------|
| 1 | Notification delivery model | `notification` table: `(id, user_id, session_id, message, created_at, read_at)`. Scheduler writes one row per Coordinator. |
| 2 | Concern promotion UX | Silent nullify with undo toast (~10s). Audit log preserves original text. |
| 3 | Slot conflict resolution | Dedicated swap action: `POST /branches/{branchId}/slots/swap`. Both slot updates in one transaction. |
| 4 | Export format | PDF and CSV. Generated on-demand, not stored. Format via `?format=pdf\|csv`. |
| 5 | Session base rate: future-dated changes | Not in Phase 2. Rate changes take effect immediately. |

---

## 22. Guiding Principles

- Clarity over cleverness
- Thin, explicit layers — routes parse, services decide, repositories query
- Never check role names at runtime — check capability codes
- All timestamps are server-stamped — clients never send "current time"
- Financial arithmetic in BigDecimal — never Double
- Every mutating request is idempotent via client-generated UUID
- The audit log is the source of truth for change history
