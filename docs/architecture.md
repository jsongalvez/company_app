# Architecture

## 1. Purpose

Living reference for all architectural decisions. When confused, refer here. When things change, update here.

**Audience:** AI agents and developers working on this codebase.

---

## 2. Problem Statement

Internal operations app for a multi-branch physical therapy practice. Practitioners log sessions, coordinators handle finance and remittance, owners manage users and branches.

All branches share one cloud-hosted backend and one database. Each branch maintains its own inventory stock.

**Platforms:** Web (desktop and phone browsers)
**Constraints:** Small team, free tools/hosting, clarity over speed.

---

## 3. High-Level Architecture

```
Web app (browser: desktop and phone)
        ↓
    HTTPS (TLS)
        ↓
Kotlin Backend (Javalin) — hosted on Oracle Cloud (singapore west)
        ↓
PostgreSQL Database
```

Kotlin backend (contracts included); the web app (Svelte + TypeScript) is served by the same backend and typed from its OpenAPI spec. All branches connect to a single cloud-hosted backend. There is no per-branch server.

---

## 4. Technology Stack

| Layer | Technology |
|-------|-----------|
| Web UI | Svelte 5, Vite, TypeScript (types generated from the OpenAPI spec) |
| Backend | Kotlin (JVM), Javalin 7.2.2, HikariCP, Exposed DSL, kotlin-logging |
| Database | PostgreSQL 18, Flyway |
| Infrastructure | Docker, Docker Compose, Oracle Cloud Free Tier (singapore west), Coolify |
| Quality | Detekt, Ktlint |
| Testing | JavalinTest, JMH (manual diagnostics) |

**Non-goals:** No Node/Express server, no Spring Boot, no paid tooling, no native mobile/desktop apps (the web app covers phone and desktop).

---

## 5. Hosting & Deployment

Single cloud backend + single database serving all branches over HTTPS.
Coolify builds `backend/Dockerfile` (backend plus the built web app, served from
one port via `WEB_DIR`). Standing shape:
Oracle Cloud free tier, Coolify-managed Postgres on the same VM, TLS via
Let's Encrypt, tailnet-only admin SSH. Branch clients use the public HTTPS
endpoint; no VPN.

---

## 6. Repository Structure

```
company-app/
├── web/                      # Svelte web app (Vite); src/api/schema.d.ts generated from OpenAPI
├── backend/
│   └── src/main/kotlin/com/companyb/companyapp/
│       ├── identity/         # Account lifecycle: auth, users, credentials, /me (map #533 #537)
│       ├── authorization/    # Capability checks, grant storage, read scoping, route filter adapter (#538)
│       ├── audit/            # Audit append seam, scoped reads, registry (#549)
│       ├── branch/           # Branch aggregate: records, storage, routes (#536)
│       ├── branchday/        # Operational-day identity, state gates, locking seams (#536)
│       ├── workforce/        # Attendance, assignments, membership + duty-cutoff seam; relief/ subcluster for requests/invites/delegates (#539 #540)
│       ├── client/           # Client records, patch policy, anonymization + session lock/read seam (#541)
│       ├── session/          # Session aggregate, practitioners, concerns/promotion, void state, rates + preview; dashboard/ subcluster for today + bearer detail reads; SessionReads seam (#542 #553)
│       ├── commerce/         # Catalog, stock ledger, product-sale cluster + CommerceReads seam (#543)
│       ├── finance/          # Day-entry mutation policy: expenses, compensations, allowances + FinanceReads seam (#546)
│       ├── commission/       # Commission engine, split materialization, manual inclusions + local fact-read projection (#544)
│       ├── remittance/       # Remittance aggregate, draft/submit/undo commands, internal stores + tables, route adapters (#545)
│       ├── reporting/        # Summary projections, cursors, report assembly + Excel/PDF rendering; internal stores + tables, route adapters (#547)
│       ├── notification/     # Mailbox, occurrence idempotency, appointment reminders + session-access reads; internal stores + tables, route adapters; NotificationReads/NotificationAppender seams (#550)
│       ├── http/             # Transport + serialization + OpenAPI contract; openapi/ subcluster for canonical document/projector/export (#551)
│       ├── app/              # Startup composition: AppConfig + SchedulerLifecycle executor with composition-supplied jobs (#551)
│       ├── observability/    # Incident packets/delivery, feedback adapters (#551)
│       ├── database/         # HikariCP + Flyway + Exposed setup
│       ├── logging/          # Logback converters, logging extensions
│       ├── api/              # Thin remaining adapters: health probe, context extensions, trace filter, route utils
│       ├── exception/        # Domain exception hierarchy
│       ├── utils/            # Stateless mechanism helpers (opaque cursor codec, id generator)
│       └── Main.kt           # Explicit composition root (#551)
├── docs/
│   ├── architecture.md       # This file — system/ownership overview with pointers
│   ├── business-requirements.md  # Intended behavior and domain rules
│   ├── engines.md            # Commission/delegate/remittance pseudocode
│   ├── research/             # Accepted research snapshots (not policy)
│   ├── adr/                  # Architecture decision records
│   └── specs/                # Feature specs
├── tools/performance/        # JMH baseline checker
└── docker/
```

Transitional remnants (thin `api/` adapters) stay until their owning map children
land; later children update this tree incrementally and never pre-document
unimplemented paths. The `service/` remnant dirs and the shared `repository/`
bucket are gone (#607): the opaque cursor codec lives at `utils/` under the
mechanism owner, and resurrected legacy/shared paths fail ownership
classification.

### Module Dependencies

```
web ──typed by──> backend OpenAPI spec (npm run gen:api)
```

- `backend` is JVM-only (Javalin, JDBC)

---

## 7. Backend Layering

One mutation transaction per command ([ADR-0024](adr/0024-command-owned-mutation-transactions.md)):
the owning feature command opens exactly one DB transaction, performs the
mutation, and records its audit row inside it. Repository mutators are
`*InTransaction` store operations that open no transaction; routes are thin
adapters (parse → call one command; capability before-filters).

Feature packages under `com.companyb.companyapp` are the semantic modules;
Kotlin `internal` does not stop sibling imports, so seams are protected
executably, not by prose (see below). Cross-feature reads go through declared
read seams (`SessionReads`, `CommerceReads`, `FinanceReads`, `WorkforceReads`,
`NotificationReads`/`NotificationAppender`, `AuthorizationGrants`); direct
store/table imports across owners stay banned. Read projections perform
intentional batched joins only in files holding an explicit recorded grant
(`BackendArchitectureOwners.allowedProjectionReads`, map #615 #608) —
`internal` visibility alone permits nothing, FK `references()` edges and
`tableName` metadata need no grant, and mapped Views stay read-only even for
their owner; command coordination stays in the owning command.

The pre-#533 Routes/Services/Repositories layer prose is superseded by the
above and by [`docs/deep-modules.md`](deep-modules.md) — the authoritative
owner/seam map. New backend logic starts there. Auth mechanics (JWT issue/verify,
bcrypt, rate limiting, persisted revocation) live in `identity/`; connection
lifecycle (HikariCP, Flyway) lives in `database/` — both pointed at from
[`docs/deep-modules.md`](deep-modules.md).

### Enforced boundaries (#324)

These dependency rules are executable, not prose: `SemanticOwnershipArchitectureTest`
(backend test source set) fails CI on violations and pins allowed/forbidden shapes.

- `api/**` stays an HTTP adapter — no Exposed imports, transaction blocks, persistence-table
  imports, or raw-SQL exec (the health probe lives in `database/DatabaseHealth`).
- In feature packages, persistence-table knowledge appears only inside `internal object` bodies
  (`*Audit` seams, `*Repository` stores) — public command/service surfaces stay table-free;
  feature-local stores are declared `internal`.
- Audit writes are owned by feature seams/commands inside the command's transaction — never
  from a shared persistence layer; the retired `auditFn` callback stays gone.

---

## 8. Deep Module Map

Moved to [`docs/deep-modules.md`](deep-modules.md) — the authoritative map of deep modules (BranchDay, Finance, Capability middleware, Audit, Session, Inventory, Auth, Attendance) and where new backend logic belongs.


## 9. Auth & Authorization

### 9.1 JWT Authentication

- Issued on login, attached to all subsequent requests as `Authorization: Bearer <token>`
- Expiry: 24 hours
- Secret: long random string in `.env` — never commit

### 9.2 Capability-Based Authorization

**Rule:** Never check a role name in service logic. Always check a capability code.

Roles are predefined bundles of capabilities — seeded in V2, then resolved at runtime exclusively through the view (GLOBAL management codes and all-branch `VIEW_BRANCH_DATA` for SUPERUSER/OWNER/ACCOUNTANT derive from the role; branch-scoped codes derive from ACTIVE assignments, V21/#417; V25/#431 adds OWNER's global read — former migration numbers kept as provenance; all folded into the V1 baseline, §10). The only query that matters is:

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

Capability gates live at the route layer as Javalin before-filters ([ADR-0007](adr/0007-route-level-capability-gates.md)):
each route declares its required capability, with resource-specific resolution in
feature-local policy helpers (`SessionAuthz`, `ExpenseAuthz`, `RemittanceAuthz` —
#605) over `Access.require` with an `Act` (`authorization/Access.kt`). The service
layer retains day-state assertions (`BranchDayService.checkBranchDayEditable`),
not capability checks. Documented exceptions keep service/bearer-level enforcement:
user assignment/slot self-service rules (#134), the bearer-only session-detail read
(#152), and the universal post-clock-in dashboard read. Day-scoped relief edits additionally accept a `BRANCH_DAY` grant for
the day (the #157 OR shape; GLOBAL never satisfies those gates); single-day reads
gained day legs under the same strictness (#158).

### 9.3 Capability Codes

Capability codes are fixed contract values, not runtime-created values. V2 seeds the initial
capability catalog in `backend/src/main/resources/db/migration/V2__seed_roles_capabilities.sql`
(folded #461: base bundle plus `RECEIVE_NEXT_APPOINTMENT_ALERTS` and `MANAGE_CATALOG`).
Folded view legs (all in the V1 baseline — former migration numbers kept as provenance):
V21 widens the view's branch-derived leg so every
assigned role's non-management bundle derives BRANCH-scoped from ACTIVE assignments, and
V25 adds OWNER to the GLOBAL `VIEW_BRANCH_DATA` leg. V26 introduces
`MANAGE_CATALOG` as the GLOBAL-scoped shared-catalog authority (map #422
decision #436, Option 3): product/category collection and detail routes gate
GLOBAL `MANAGE_CATALOG`, derived GLOBALly for SUPERUSER/OWNER/MANAGER/COORDINATOR
and excluded from the BRANCH-derived leg, so catalog writes stay global while
BRANCH `MANAGE_PRODUCTS` keeps governing branch inventory and base rates. The application references codes through
`backend/src/main/kotlin/com/companyb/companyapp/contracts/authorization/AuthorizationContracts.kt`; migration SQL
keeps its database-owned string literals.

| Code | Scope | Who holds it |
|------|-------|--------------|
| `VIEW_BRANCH_DATA` | BRANCH / GLOBAL (SUPERUSER, OWNER, ACCOUNTANT) | Coordinator, Owner, Practitioner, Accountant |
| `EDIT_BRANCH_DATA` | BRANCH / BRANCH_DAY | Coordinator, Owner (OPEN days only), relief users |
| `VOID_SESSION` | BRANCH | Coordinator |
| `SUBMIT_REMITTANCE` | BRANCH | Coordinator |
| `ASSIGN_COMPENSATION` | BRANCH | Coordinator, Owner |
| `MANAGE_USERS` | GLOBAL | Owner, Manager |
| `MANAGE_PRODUCTS` | BRANCH | Owner (home branch only), Coordinator (assigned branches) — branch inventory and base rates only, never the shared catalog |
| `MANAGE_CATALOG` | GLOBAL | Coordinator, Owner, Manager, Superuser — shared product/category catalog |
| `ASSIGN_DELEGATE` | GLOBAL | Owner, Manager |
| `EDIT_PAST_DAY` | BRANCH | Coordinator only (PAST/REMITTED days) |
| `RECEIVE_NEXT_APPOINTMENT_ALERTS` | BRANCH | Coordinator with an active assignment to the branch |
| `VIEW_COMMISSION` | BRANCH / BRANCH_DAY | Branch members, relief on its day, helpers until removed, Superuser. Never GLOBAL: the Owner and Accountant see no commission ([ADR-0025](adr/0025-commission-is-private-to-the-branch.md)) |

**Rule:** Owner does NOT hold `EDIT_PAST_DAY`. The branch state machine enforces Coordinator-only editing on PAST/REMITTED days.

`RECEIVE_NEXT_APPOINTMENT_ALERTS` is inserted and initially role-linked by V2 (folded
from V5, #461), not by a later migration. V21
derives its branch-scoped capability from active Coordinator roles and active branch assignments;
it is not a GLOBAL role-derived capability because role membership alone cannot identify a branch.

### 9.4 context_type Enum Usage

`MEDICAL_MISSION` and `PROVINCIAL_TOUR` values in `capability_context_type` are reserved for future differentiation only. Current BRANCH grants use the relevant `branch.id` as `context_id`; GLOBAL grants use the nil UUID.

### 9.5 Medical Mission Delegate Hook

Implemented as a service-layer hook, not a trigger. Both the delegate record and the capability row are written in a single transaction.
The grant is `EDIT_BRANCH_DATA` at the branch. View leg (a2) adds `VIEW_BRANCH_DATA`, `VOID_SESSION` and `VIEW_COMMISSION` to it, so a helper has relief's rights with no end day until removed (V23, owed #28, [ADR-0025](adr/0025-commission-is-private-to-the-branch.md)).

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

`active_user_capabilities` joins `app_user` and filters `status = 'ACTIVE'`. Setting a user to `INACTIVE` immediately revokes all capability checks. The session token itself is rejected by the persisted revocation check in `UserRepository.authorize`: logout, password reset, and deactivation advance `app_user.jwt_revoked_at`, and a token verifies only when its `iat` is strictly later than that boundary (#492). Reactivate never clears the boundary, so old JWTs stay dead.

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
- The launch set (Nov 1 2026) is four files, one per concern: `V1__schema.sql`
  (tables, types, indexes, triggers, money views), `V2__access.sql` (roles,
  capability codes, the role matrix and the `active_user_capabilities` view),
  `V3__common_concerns.sql` (starter concern list) and `V4__telemetry.sql`
  (usage events, placed on the `telemetry` tablespace where one exists). It
  replaced the 24-file chain at commit `acd24bac`;
  `tools/database/migration-equivalence.sh` proves both build the same schema
  and reference data.
- Evolve the schema by adding V5 and up. Never edit a migration a database has run.
- Destructive changes (DROP, RENAME) get their own migration with a comment explaining why
- Application startup runs `migrate()` only. Operators must stop the application and run
  `flyway -url=<jdbc-url> -user=<user> -password=<password> repair` explicitly after reviewing
  migration history; repair is never an automatic startup action. Never repair a
  database whose actual schema differs from its history — reset it instead.

The migration directory is authoritative. Reason about current schema from the
files in `backend/src/main/resources/db/migration/`.

**Development reset (deterministic recreate from the launch set):**
- Stop the backend. Drop and recreate only the identified database — the
  development database (`$POSTGRES_DB` from the root `.env`) or, for the k6/test
  path, the test database (`$TEST_DB_NAME`, default `company_app_test`); never an
  external database — then start the backend, whose `DatabaseConfig.initialize`
  runs `flyway.migrate()` from the launch set:
  ```bash
  set -a; . ./.env; set +a
  : "${POSTGRES_DB:?root .env must define POSTGRES_DB}"
  dropdb -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" "$POSTGRES_DB" &&
  createdb -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB" &&
  ./gradlew :backend:run
  ```
  (`PGPASSWORD` carries the password non-interactively; quoting keeps unusual
  host/user values intact. Substitute `$TEST_DB_NAME` for `$POSTGRES_DB` to reset
  the test database instead.)
- Test worker schemas need no manual step: each backend-test JVM provisions its
  own owned schema from the same baseline and drops it on shutdown; per-test
  isolation is `TestDatabaseLifecycle.resetWorkerSchema`.
- Databases still carrying the retired V3–V6 history fail Flyway validation
  (checksum/missing-version) instead of silently repairing — that red state is
  the signal to run the reset above.

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

Command-owned per ADR-0024. Architecture-level facts kept here: every INSERT, UPDATE,
and soft-DELETE to a registered table gets an audit entry inside the
command's transaction; the audited-table registry (`AuditLogTableRegistry`,
served live at `GET /api/audit-log/tables`) is authoritative — a missing
entry makes rows invisible to the UI table filter. Writes to REMITTED-day
records set `is_flagged = true` and require a `reason`; coordinators triage
`WHERE is_flagged = true AND acknowledged_at IS NULL`. Shapes are enforced by
`SemanticOwnershipArchitectureTest` (§7), not this prose.

Mechanism/derived writes stay unaudited at their own tables when the domain
event is audited where it happens (`commission_split` recalculation output,
relief/delegate `user_capability` grant rows, lazy `branch_day` bootstrap,
attendance-created `branch_day_assignment`, sale-generated movements);
notification writes carry an explicit ADR-0024 exception.

---

## 13. Attendance

Behavioral rules live in [`docs/business-requirements.md`](business-requirements.md)
(Attendance & Presence, Relief Duty); the owner is `workforce/`
([router](deep-modules.md)). Architectural facts kept here: multiple rows per
`(user_id, branch_day_id)` for multiple shifts; the partial unique index
`idx_one_active_clock_in WHERE clock_out IS NULL` allows one open window;
`is_relief` derives from the active home assignment at clock-in; commission
eligibility intersects `sold_at` with open attendance windows (engine detail
in [`docs/engines.md`](engines.md)).

---

## 14. Sessions

Type ladder, pricing, concerns, and void policy live in
[`docs/business-requirements.md`](business-requirements.md) (Sessions);
concurrency and gate mechanics live in `session/` code; the
owner is `session/` ([router](deep-modules.md)). Architectural facts kept
here: type is a creation-time snapshot (later voids never rewrite earlier
types); one PENDING session per client globally (service pre-check + partial
unique index backstop + client row lock); the `no_rate_overlap` EXCLUDE
constraint on rate periods; every branch is provisioned with five default
rates at creation so session create never fails for want of a rate row.

---

## 15. Inventory

Stock semantics live in [`docs/business-requirements.md`](business-requirements.md)
(Products and Inventory); the owner is `commerce/` ([router](deep-modules.md)).
Architectural facts kept here: every movement row carries `branch_day_id`
(day-state gated); `branch_inventory.version` optimistic locking with 409 on
mismatch; the sale path (stock update + sale + movement) is one transaction
with a below-zero guard; sign/notes rules are DB-enforced per movement type.

---

## 16. Finance

Engines live in [`docs/engines.md`](engines.md) (commission recalculation,
remittance submission); owners are `finance/` + `commission/` + `remittance/`
([router](deep-modules.md)). Architectural facts kept here: `BigDecimal`
throughout, `NUMERIC(15,4)` splits; `SERIALIZABLE` submission with optimistic
`remittance` versioning; snapshot immutability via trigger (SESSION flow —
PRODUCT totals derive from `remittance_line`); `no_remittance_overlap` covers
SUBMITTED rows only, drafts are unconstrained.

---

## 17. Connectivity & Reliability

The online model, optimistic UI, and retry posture live in
[`docs/business-requirements.md`](business-requirements.md) (Connectivity and
Reliability). Architectural facts kept here: client-generated UUID PKs make
INSERTs idempotent (duplicate PK returns the existing row); `version`
optimistic locking on `session`, `branch_inventory`, `remittance` (409 on
mismatch; all else last-write-wins); all timestamps server-stamped (UTC
`TIMESTAMPTZ`); the 04:00 Asia/Manila day boundary evaluates lazily per
request, never by cron.

### Wire enum evolution policy

Wire enums in `contracts/` carry an `UNKNOWN` forward-compat sentinel (#876): a
client that predates a new server value decodes it as `UNKNOWN` instead of
failing the whole response, and degrades that row (read-only, grants nothing).
`UNKNOWN` is never persisted and never sent; the backend rejects it on input.
Add new values at the end; never rename or remove one that may be stored.

---

## 18. Environment Configuration

Secrets loaded from `.env` via `dotenv-kotlin`. Never commit `.env`.

```
POSTGRES_DB=, POSTGRES_USER=, POSTGRES_PASSWORD=
APP_PORT=, JWT_SECRET=, JWT_ISSUER=, JWT_AUDIENCE=
SMTP_HOST=, SMTP_PORT=, SMTP_USERNAME=, SMTP_PASSWORD=, SMTP_FROM=
TZ=Asia/Manila
```

All `SMTP_*` values are required for email delivery. If absent or incomplete, password-reset
delivery fails closed (#897): codes stay valid for retry but neither the code nor the account
identifier reaches the logs, and startup emits a warning. The server-log relay is dev-only
behind the explicit `PASSWORD_RESET_DEV_RELAY=true` opt-in — never enable it where logs ship.

---

## 19. Developer Setup

Commands live in the root `README.md`. No duplicate command ledger is kept here.

---

## 20. Development Phases

Retired as a live ledger (map #533 #571): phase/progress history now lives in
issue history and the map's Decisions-so-far, not in a tracked document. The
standing facts that were here remain true and are pointed at, not re-listed —
backend core wired (Javalin + HikariCP + Flyway, JWT/bcrypt/tracing),
business features under their owners above, client applications, deployment
via the runbook (§5).

---

## 21. Resolved Decisions

Historical record — new decisions land in `docs/adr/` and issue history, not
in this table.

| # | Item | Decision |
|---|------|----------|
| 1 | Notification delivery model | **Superseded (#571):** per-recipient rows remain, but the live shape adds `branch_id`, occurrence `dedup_key` (UNIQUE with `user_id`), and read-state columns — see `notification/` ([router](deep-modules.md)) and the V1 schema. Original text preserved: `notification` table: `(id, user_id, session_id, message, created_at, read_at)`. Scheduler writes one row per Coordinator. |
| 2 | Concern promotion UX | Silent nullify with undo toast (~10s). Audit log preserves original text. |
| 3 | Slot conflict resolution | Dedicated swap action: `POST /branches/{branchId}/slots/swap`. Both slot updates in one transaction. |
| 4 | Export format | Excel (.xlsx) and PDF. Generated on-demand, not stored. Format via `?format=xlsx\|pdf`. |
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
