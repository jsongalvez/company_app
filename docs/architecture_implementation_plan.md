# Architecture Implementation Plan — Phase 2

> **How to read this document.** Each section names an architectural decision or
> implementation requirement, states the decision taken (with reasoning where
> non-obvious), and describes what must exist in code before the feature is
> considered done. Sequencing is in the final section.

---

## AUTH

### A1 — Capability Check Path (Authoritative)

**Decision:** All permission checks go through `active_user_capabilities` view, never
against `user_role` directly. Role membership is only used to seed the initial
capability set — never queried at runtime.

**Implementation:**

The service layer exposes one method:

```kotlin
fun hasCapability(
    userId: UUID,
    capabilityCode: String,
    contextType: CapabilityContextType,
    contextId: UUID
): Boolean
```

It queries `active_user_capabilities` (which already filters inactive users and
expired time windows) for a matching row. Returns `true` if any row is found.
Called at the top of every mutating service method — not in routes, not in
repositories.

**Rule:** Never call `SELECT role FROM user_role` in service logic. If you are
tempted to check a role name, you need a capability code instead.

---

### A2 — Capability Codes (Seed Data, V2 Migration)

All capability codes are inserted as seed data in `V2__seed_capabilities.sql`.
They are constants — not dynamic. The application references them by their string
code, which is defined as an enum/sealed class in `shared/domain/`.

**Initial codes (non-exhaustive — expand as features are built):**

| Code | Scope | Who holds it |
|------|-------|--------------|
| `VIEW_BRANCH_DATA` | BRANCH | Coordinator, Owner, Practitioner, Accountant |
| `EDIT_BRANCH_DATA` | BRANCH / BRANCH_DAY | Coordinator, Owner (OPEN days only), relief users (via user_capability) |
| `VOID_SESSION` | BRANCH | Coordinator |
| `SUBMIT_REMITTANCE` | BRANCH | Coordinator |
| `ASSIGN_COMPENSATION` | BRANCH | Coordinator, Owner |
| `MANAGE_USERS` | GLOBAL | Owner, Manager |
| `MANAGE_PRODUCTS` | GLOBAL | Owner, Coordinator |
| `ASSIGN_DELEGATE` | GLOBAL | Owner, Manager |
| `EDIT_PAST_DAY` | BRANCH | Coordinator only (PAST/REMITTED days) |

**Rule:** Owner does NOT hold `EDIT_PAST_DAY`. The branch state machine enforces
Coordinator-only editing on PAST/REMITTED days. Owner edit access is limited to
OPEN days.

---

### A3 — context_type Enum Usage (Decision: Locked)

`MEDICAL_MISSION` and `PROVINCIAL_TOUR` values in `capability_context_type` are
**reserved for future differentiation only**. All current capability grants use
`BRANCH` with the relevant `branch.id` as `context_id`. This is the only path
auth checks traverse in Phase 2.

Any future code that branches on `context_type = MEDICAL_MISSION` must be
preceded by an ADR.

---

### A4 — Medical Mission Delegate Hook

Implemented as a service-layer hook, not a trigger. Both the delegate record and
the capability row are written in a single transaction.

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

removeDelegate(delegateId):
  BEGIN
    UPDATE medical_mission_delegate SET ended_at = now()
    UPDATE user_capability SET valid_to = now()
      WHERE source_type = MEDICAL_MISSION_DELEGATE AND source_id = delegateId
  COMMIT
```

The `active_user_capabilities` view's time-window filter (`now() <= valid_to`)
does the rest. No separate revocation step needed.

---

### A5 — Inactive User Revocation

`active_user_capabilities` joins `app_user` and filters `status = 'ACTIVE'`. This
means setting a user `INACTIVE` immediately revokes all capability checks. The
session token itself must also be rejected.

**JWT revocation:** Maintain a server-side deny list keyed by `userId`. On user
deactivation, add the user to the deny list. JWT middleware checks the deny list
before the capability query. Deny list is an in-memory `ConcurrentHashMap<UUID,
Instant>` (evicted after JWT max expiry window — 24h). On server restart it
repopulates from `app_user WHERE status = 'INACTIVE'` at startup.

**Rule:** Do not add a `jti` claim or full token blacklist. The userId-level deny
list is sufficient for this domain — individual token revocation is not a
requirement.

---

### A6 — Relief Access Grant Flow

1. Relief user clocks into a non-home branch → `branch_day_assignment` row
   created with `is_relief = true`. View-only access is implicit from this row.
2. Relief user selects a target checked-in user → `grant_relief_access` row
   created with status `PENDING`.
3. Target user grants → status updated to `GRANTED`. Service layer writes
   `user_capability` row:
   ```
   source_type = RELIEF_ACCESS
   source_id   = grant_relief_access.id
   context_type = BRANCH_DAY
   context_id   = branch_day.id
   valid_to    = (branch_day.date + 1 day) at 04:00 Asia/Manila, stored as UTC
   priority    = 10
   ```
4. Partial unique index `idx_one_grant_per_day` prevents a second GRANTED row for
   the same `(requested_by, branch_day_id)`.
5. Access expires naturally via `valid_to`. No cleanup job needed.

**Multiple pending requests:** A relief user may have concurrent PENDING rows
targeting different users. First grant wins (enforced by the partial unique index
on GRANTED). Subsequent grant attempts for the same `requested_by + branch_day_id`
are silently ignored (duplicate row rejected by index).

---

## ATTENDANCE

### B1 — Clock-In / Clock-Out Model

`attendance` supports multiple rows per `(user_id, branch_day_id)` to allow
multiple shifts. The partial unique index `idx_one_active_clock_in
WHERE clock_out IS NULL` enforces that only one window is open at a time.

**Branch Day Assignment prerequisite:** The service layer must create or verify a
`branch_day_assignment` row before inserting the first `attendance` row. Both are
written in the same transaction:

```
clockIn(userId, branchId, markedBy):
  BEGIN
    bd = UPSERT branch_day (branch_id, today) ON CONFLICT DO NOTHING RETURNING id
    UPSERT branch_day_assignment (branch_day_id=bd.id, user_id, is_relief=<computed>)
      ON CONFLICT DO NOTHING
    INSERT attendance (branch_day_id=bd.id, user_id, marked_by, clock_in=now())
  COMMIT
```

`is_relief` is computed by checking `user_branch_assignment WHERE user_id = ? AND
branch_id = ? AND ended_at IS NULL`. If no active home assignment exists,
`is_relief = true`.

---

### B2 — Commission Eligibility Window

The commission engine determines eligibility per sale based on the `sold_at`
timestamp intersecting any open attendance window for that user on that branch day.

**Query per sale:**
```sql
SELECT a.user_id
FROM attendance a
WHERE a.branch_day_id = :branch_day_id
  AND a.clock_in  <= :sold_at
  AND (a.clock_out IS NULL OR a.clock_out >= :sold_at)
```

This correctly handles multiple shifts: a user who clocked out for lunch and
clocked back in is eligible for sales that fall within either window.

---

### B3 — No attendance_history Table

`attendance_history` is dropped. Historical reads use `attendance` directly.
The audit log captures all edits. The monthly summary queries `attendance JOIN
branch_day` for who-was-present reports.

---

## SESSIONS

### C1 — Session Type Assignment (Service Layer)

Session type is computed at creation time by the service layer and stored. Never
recomputed. Never manually changed.

**Algorithm:**
```
fun computeSessionType(clientId, branchType): SessionType {
  if (branchType == MEDICAL_MISSION) return MEDICAL_MISSION

  val priorCount = COUNT non-voided, non-MEDICAL_MISSION sessions for clientId

  return when {
    branchType == PROVINCIAL_TOUR && priorCount == 0 -> PROVINCIAL_FIRST
    priorCount == 0  -> REGULAR
    priorCount == 1  -> SECOND_SESSION
    else             -> SUBSEQUENT
  }
}
```

"Non-voided" means: session has no row in `active_session_voids` (the view, never
raw `session_void`).

**The void/un-void edge case (Decision: Snapshot is Final):**  
If a session is voided after later sessions were created, those later sessions
retain their computed type. The type is a snapshot at creation time — it records
what was true when the client was signed in. Coordinators who need to correct a
type void and recreate. No auto-recalculation.

---

### C2 — Concurrent Session Guard

**Mechanism:** Partial unique index on `session (client_id) WHERE session_status =
'PENDING'`. Only one PENDING session per client globally.

**COMPLETED is not blocking.** A completed session does not prevent booking. The
business constraint is: a client cannot have two active in-progress sessions
simultaneously.

**Application-layer race protection:** Before inserting, the service acquires a
row-level lock on the client:
```sql
SELECT id FROM client WHERE id = :clientId FOR UPDATE
```
This serializes concurrent session creation for the same client. The unique index
is the final safety net; the lock is the primary guard.

---

### C3 — Session Base Rate: No Gap/Overlap

```sql
CONSTRAINT no_rate_overlap EXCLUDE USING gist (
    branch_id    WITH =,
    session_type WITH =,
    tstzrange(effective_from, effective_until, '[]') WITH &&
)
```

When a Coordinator updates a rate, the service layer must:
1. Set `effective_until` on the current active rate to `now()` (Manila time →
   UTC).
2. Insert a new rate row with `effective_from = now()`.

Never allow a gap between rates. The service must validate that no gap exists
before committing. If the UI allows a future-dated rate change, the service must
verify that `new.effective_from = old.effective_until + 1 microsecond` (or
contiguous). Reject otherwise.

---

### C4 — Concern Promotion Flow

When a Coordinator promotes an "other_concerns" string to a structured concern:

1. `INSERT INTO concern (label, created_by, created_at)` — provenance captured.
2. `INSERT INTO session_concern (session_id, concern_id)`.
3. `UPDATE session SET other_concerns = NULL` (or leave as-is if partial — TBD by
   Coordinator).
4. Audit log entry for the session UPDATE.

The `concern` table's `created_by` field distinguishes system-seeded concerns
(NULL) from Coordinator-promoted ones.

---

### C5 — Next Appointment Alerts

Implemented as a server-side scheduled task (not a DB trigger). The task runs once
daily at 07:00 Asia/Manila:

```sql
SELECT s.id, s.client_id, s.next_appointment_date, bd.branch_id
FROM session s
JOIN branch_day bd ON s.branch_day_id = bd.id
WHERE s.next_appointment_date = CURRENT_DATE + INTERVAL '2 days'
  AND s.session_status = 'COMPLETED'
  AND s.id NOT IN (SELECT session_id FROM active_session_voids)
```

Notification delivery: in-app notification to the Coordinator(s) assigned to that
branch. Notification storage model is out of scope for Phase 2 — use a simple
`notification` table: `(id, user_id, message, created_at, read_at)`.

---

## INVENTORY

### D1 — Inventory Movement and branch_day_id

Every `inventory_movement` row carries `branch_day_id`. The service resolves (or
creates) the `branch_day` row before inserting the movement — same UPSERT pattern
as attendance.

Day-state enforcement:
- `OPEN` → any authorised user can insert
- `PAST` → Coordinator only (same `EDIT_PAST_DAY` capability check)
- `REMITTED` → Coordinator only, flagged audit entry required

---

### D2 — Stock Integrity: Optimistic Lock on branch_inventory

`branch_inventory` carries a `version INT NOT NULL DEFAULT 1`.

On every stock-modifying operation:
```sql
UPDATE branch_inventory
SET current_stock = current_stock + :delta,
    version       = version + 1
WHERE branch_id  = :branchId
  AND product_id = :productId
  AND version    = :expectedVersion
```
If 0 rows updated → version mismatch → reject with a conflict error. Client retries
after refreshing the current stock value.

For sale operations, additionally: the stock update and the `product_sale` +
`inventory_movement` inserts happen in the same transaction. If `current_stock`
would go below zero, the transaction is rejected before the UPDATE executes:
```sql
-- Guard check inside the transaction:
SELECT current_stock FROM branch_inventory
WHERE branch_id = :branchId AND product_id = :productId
FOR UPDATE;
-- If current_stock + delta < 0: raise application error, rollback.
```

---

### D3 — Sign and Notes Constraints (DB-enforced)

Already in DDL (`V1__full_schema.sql`):
- `RESTOCK` → `quantity_change > 0`
- `SALE, TESTER, SAMPLE, MISSING` → `quantity_change < 0`
- `ADJUSTMENT` → either sign
- `MISSING` → `notes IS NOT NULL AND length(notes) > 0`

No application-layer enforcement needed for these — the DB constraint is the
guard.

---

## FINANCE

### E1 — Commission Recalculation Engine

Full algorithm in `engine_specifications.md` (Engine 1). Key implementation notes:

**Trigger events** (service layer hooks, not DB triggers):
- `ProductSaleService.create(...)` → triggers recalc for that `branch_day_id`
- `AttendanceService.clockIn(...)` → triggers recalc
- `AttendanceService.clockOut(...)` → triggers recalc
- `CommissionManualInclusionService.upsert(...)` → triggers recalc

**PAST/REMITTED days:** Recalc is blocked automatically unless the calling user
holds `EDIT_PAST_DAY` and explicitly initiates a manual re-run through a dedicated
Coordinator UI action.

**Precision:** All intermediate arithmetic in `BigDecimal` (Kotlin). Never
`Double`. Write `NUMERIC(15,4)` to DB.

**Batched upsert:** Accumulate all user totals in memory first, then flush in a
single batch (see Engine 1, step 5). Never interleave reads and writes per-user.

---

### E2 — Manual Commission Inclusion/Exclusion

`commission_manual_inclusion` stores overrides keyed to a specific
`(product_sale_id, user_id)`. When the engine processes a sale, it applies these
overrides after computing the attendance-based eligible set:

- `is_included = true` → force-add user even if not clocked in at `sold_at`
- `is_included = false` → force-remove user even if clocked in

The engine re-runs the full recalculation for the affected `branch_day_id`. The
`commission_split` table stores the summed result — one row per user per day.

---

### E3 — Compensation Uniqueness

One compensation payout per user per paying branch per day:
```sql
UNIQUE INDEX idx_compensation_unique ON compensation (user_id, paying_branch_day_id)
```

For relief duty, `work_branch_day_id ≠ paying_branch_day_id` is allowed and
expected. The unique constraint is on `paying_branch_day_id` — a user cannot be
paid from the same branch drawer twice in one day.

---

### E4 — Remittance Submission (Atomic, Serializable)

Full algorithm in `engine_specifications.md` (Engine 3). Key implementation notes:

**Draft phase:** Multiple coordinators can view and edit the same draft. No
lock is held during drafting. The `version` field on `remittance` handles
concurrent edits — last-write-wins on draft line edits is acceptable because the
snapshot is only taken at submission.

**Submission phase:**
- `SERIALIZABLE` transaction isolation.
- `SELECT FOR UPDATE` on the `remittance` row.
- Version check before proceeding.
- Snapshot written, status updated, branch_days transitioned — all in one
  transaction.

**Branch day transition:** All `branch_day` rows referenced by
`remittance_day_breakdown` for this remittance are set to `REMITTED`. Any rows
already `REMITTED` (from a prior product remittance if this is the session
remittance, or vice versa) are left as-is — `UPDATE ... WHERE status != 'REMITTED'`
or just `SET status = 'REMITTED'` unconditionally (idempotent).

**Exclusion constraint scope:** The `no_remittance_overlap` EXCLUDE constraint
applies only to `status = 'SUBMITTED'` rows. Drafts are unconstrained. This allows
a Coordinator to create and discard draft remittances freely without hitting
constraint violations.

---

### E5 — Remittance Financial Snapshot Immutability

The `fn_remittance_snapshot_immutable` trigger (`BEFORE UPDATE OR DELETE`) is
already in `V1__full_schema.sql`. The service layer must not attempt to insert into
`remittance_financial_snapshot` until the submission transaction is underway.

For `PRODUCT` remittances: no snapshot row is written. Totals are derived at query
time from `remittance_line.amount` (already snapshotted per line at inclusion
time).

---

## CONNECTIVITY

### F1 — Idempotency (Insert Path)

Every mutating INSERT uses a client-generated UUID as the primary key. The server:
1. Attempts `INSERT`.
2. On `UNIQUE VIOLATION` (duplicate PK) → returns the existing row as if the
   insert succeeded. The client receives a 200/201 with the original data.

This covers: session creation, product sale, inventory movement, attendance
clock-in, expense, compensation, allowance.

**Not idempotent by design:** `clock_out` (an UPDATE to an existing attendance
row). For clock-out retries, the client sends the same `clock_out` timestamp. The
server does `UPDATE ... SET clock_out = :timestamp WHERE id = :attendanceId AND
clock_out IS NULL` — idempotent because setting the same timestamp twice is a
no-op in effect, and the unique index prevents a second open window.

---

### F2 — Optimistic Locking (Update Path)

Tables requiring optimistic locking:

| Table | Reason |
|-------|--------|
| `session` | Multiple practitioners adding themselves concurrently |
| `branch_inventory` | Sales racing against restocks |
| `remittance` | Two coordinators editing/submitting |

All three carry `version INT NOT NULL DEFAULT 1`. Every UPDATE increments
`version` and checks the expected value. Reject (409 Conflict) on mismatch.
Client re-fetches and retries with the new version.

All other mutable tables (`expense`, `compensation`, `allowance`, etc.) are
last-write-wins — acceptable given team size and usage patterns.

---

### F3 — Authoritative Server Time

All timestamps (`valid_to`, `branch_day` status boundaries, `clock_in`, `sold_at`)
are stamped server-side. The client never sends a "current time" — it sends intent
(e.g., "clock me in"). The server records `now()` (UTC, stored as `TIMESTAMPTZ`).

**4 AM boundary:** The server computes `valid_to` for relief access as:
```kotlin
val branchDate: LocalDate = branchDay.date  // the branch day's calendar date
val expiryManila: ZonedDateTime =
    branchDate.plusDays(1).atTime(4, 0).atZone(ZoneId.of("Asia/Manila"))
val expiryUtc: Instant = expiryManila.toInstant()
```

Day-state transitions (OPEN → PAST) are lazy — the server evaluates
`branch_day.date < today (Manila)` on each request, not via a cron job.

---

### F4 — Strictly Online Model

No offline queue, no local-first storage, no sync conflict resolution. The client
shows optimistic UI (assumed-success state), retries on connection failure, and
reverts to error state only on definitive server rejection (4xx). Network timeouts
are retried with the same idempotency key until a definitive response is received.

---

## AUDIT LOGGING

### G1 — Rules

- Audit entries are written in the **service layer only**. Never in routes. Never
  in repositories.
- Every INSERT, UPDATE, and soft-DELETE to financial and operational tables gets an
  audit entry.
- Financial tables: `session`, `session_void`, `product_sale`, `compensation`,
  `commission_split`, `expense`, `remittance`, `remittance_line`,
  `inventory_movement`.
- Operational tables: `attendance`, `branch_day`, `user_branch_assignment`.

### G2 — Flagging Policy

Any write to a record that belongs to a `REMITTED` branch day:
- Sets `is_flagged = true` on the audit entry.
- Requires a non-null `reason` field (enforced at the service layer — reject the
  request if reason is absent).
- Surfaces in the Coordinator alert dashboard: `WHERE is_flagged = true AND
  acknowledged_at IS NULL`.

---

## SEQUENCING

Implement in this order. Do not start a phase until the prior one's DB layer is
done.

### Phase 2A — Foundation (no features yet)
1. `V2__seed_roles_capabilities.sql` — roles, capabilities, role_capability rows
2. `CapabilityService.hasCapability(...)` + `active_user_capabilities` view wired
   into middleware
3. JWT deny list for inactive users
4. `BranchDayRepository.resolveOrCreate(branchId, date)` — the UPSERT used
   everywhere

### Phase 2B — Attendance + Auth
5. Clock-in / clock-out endpoints with `branch_day_assignment` creation
6. Relief access request + grant flow + `user_capability` write
7. Medical mission delegate assign/revoke + `user_capability` write

### Phase 2C — Sessions
8. Client CRUD + search (`pg_trgm`)
9. Session create (type computation, concurrent session guard, base rate snapshot)
10. Session status updates + void/un-void
11. Session practitioner management

### Phase 2D — Inventory
12. Product + category CRUD
13. `branch_inventory` management + `inventory_movement` with day-state enforcement
14. Product sale create (in-session + walk-in + anonymous)

### Phase 2E — Finance
15. Compensation assign
16. Commission recalculation engine
17. Commission manual inclusion
18. Expense CRUD
19. Allowance assign

### Phase 2F — Remittance
20. Remittance draft create + line management
21. Remittance submission (atomic, serializable, snapshot)
22. Branch day REMITTED transition

### Phase 2G — Reporting
23. `daily_sales_summary` view
24. `monthly_remittance_summary` view
25. Export endpoints (daily, monthly, all-time, provincial, medical mission)
26. Next appointment alert task

---

## OPEN ITEMS

| # | Item | Owner | Status |
|---|------|-------|--------|
| 1 | Notification delivery model (in-app table schema) | — | Not started |
| 2 | `concern` promotion UI flow — does nullifying `other_concerns` require coordinator confirmation? | — | Needs decision |
| 3 | Slot conflict resolution — UI for practitioners swapping slots | — | Not started |
| 4 | Export format — PDF, CSV, or both? | — | Needs decision |
| 5 | Session base rate: future-dated changes — is this needed in Phase 2? | — | Needs decision |
