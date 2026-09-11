# Business Requirements — Phase 2

---

## The Business

A physical therapy and wellness practice where practitioners work on clients on a per-session basis. The business operates across multiple branches in different locations. All branches share one cloud-hosted backend and one database. Each branch maintains its own inventory stock.

---

## Terminology

- **Practitioner** — the person who works on clients
- **Coordinator** — handles finance, remittance, and branch administration

---

## Users of the App

Practitioners use the app primarily on a laptop (Windows), with Android and iOS as secondary devices. All practitioners use mobile hotspots — no shared office network.

### Auth Model

Auth checks are always against **capabilities**, not role names directly. Roles are predefined bundles of capabilities. A user may hold multiple roles. The `user_capability` table handles temporary or scoped grants (e.g. relief access, medical mission delegation) within the same system without requiring role changes.

### Roles

| Role | Description |
|------|-------------|
| `SUPERUSER` | Secret developer/owner god mode. Full access to everything. |
| `OWNER` | Business owner. Edit access: home branches + checked-in branch that day. View-only: all branches. Can manage all users. Can work on clients. No special edit rights over branch financial records — Coordinator is the sole owner of PAST and REMITTED record edits. |
| `MANAGER` | A superset of COORDINATOR — all coordinator permissions apply, plus: assigns home branches to all users directly, and manages medical mission delegation. Can work on clients. |
| `COORDINATOR` | Handles finance and remittance for assigned branch(es). Views assigned branches only. Sole editor of PAST and REMITTED records for their branch. Can work on clients. |
| `PRACTITIONER` | Logs sessions, views clients, manages inventory. Views all home branches, plus any branch checked into that day. |
| `ACCOUNTANT` | Read-only. Views sales of all branches. |
| `ONBOARDING` | Zero permissions. Freshly registered account — locked out until assigned to a branch. |

There is no TEMPORARY role — relief access is a behavioral state managed by `grant_relief_access`, not a permanent role assignment.

---

## Branches

- A branch is either a permanent physical clinic or a periodic off-site location (e.g. one practitioner visits every two weeks)
- Each branch can have one or more assigned Coordinators — set by Owner or Manager, permanent until changed. No hard limit on the number of coordinators per branch.
- A Coordinator can be assigned to multiple branches and does not need to be physically present
- For solo or periodic branches, the practitioner hands over money to the Coordinator remotely
- A practitioner can be assigned to more than one home branch simultaneously
- Home branch assignment is performed directly by Owner or Manager — there is no request flow from the practitioner

### Branch Types

| Type | Description |
|------|-------------|
| `CLINIC` | A permanent physical location |
| `PROVINCIAL_TOUR` | A temporary off-site event, treated as its own standalone branch |
| `MEDICAL_MISSION` | A free event. Always its own branch record, even if physically hosted at an existing clinic location |

---

## Branch Slot Ordering

Each user has a **slot number per assigned branch** (e.g. Practitioner 1, Practitioner 2). Slot 1 is the senior position within that branch.

- Slot is per branch assignment — a user can be Practitioner 1 at Branch A and Practitioner 3 at Branch B simultaneously
- Slot is cosmetic and user-managed — it exists purely to control display order in reports
- A user can set their own slot number on any active assignment (edit where you show up); conflicts are resolved manually via user-to-user coordination (swapping slots)
- Slot ordering determines the display order of practitioners in reports (monthly summary, daily sales)
- Within a session, practitioners are ordered by slot number
- Relief practitioners (checked into a non-home branch) appear after all home branch slots in reports for that branch
- Slot history is not tracked — current value only

---

## Branch Access and History

Branch access is determined by the user's current and past assignments:

- **Current assignment** — full operational access (read + write per role)
- **Past assignment (ended)** — read-only, records scoped up to the day the assignment ended
- **Reassigned back** — full operational access resumes; all historical records are accessible again since data was never removed

Records are updated in-place. The full change history — who changed it, when, and what the previous value was — is preserved in the audit log. No record is ever hidden from authorized users; only the access window changes based on branch assignment.

---

## Attendance & Presence

- A practitioner taps a button when they first open the app for the day to indicate they are present at a selected branch
- Attendance is recorded with **Clock-In** and **Clock-Out** timestamps
- Practitioners can also mark other members of their home branch as present or absent
- A Coordinator can also mark practitioners present at their branch
- Attendance determines:
    - Which branch's data the user can access that day (in addition to their home branch(es))
    - Whether they qualify for product commission splits that day (based on `sold_at` time vs `clock_in/out` window)
    - Who appears in the daily compensation assignment list
- Data can be viewed outside working hours — there is no time-bounded access cutoff

---

## Relief Duty

Relief duty applies when any user — Practitioner or Coordinator — checks into a branch that is not one of their home branches.

### Access

- On check-in to a non-home branch, the user automatically has **view-only access** to all branch data for that day
- To gain edit access, the relief user raises **one broadcast request for the whole branch** — no specific recipient is selected
- Requests name a branch and a date (**today by default, or any future date**); past dates are rejected
- **Flood control:** one live request per requester per branch per date; after a denial, expiry, or cancellation they may ask again
- Any active member of that branch can **grant**, **deny**, or **cancel** a pending request; the requester may **withdraw** their own while it is pending
- All retraction locks once the requester clocks in as relief at that branch — the request then stands or falls with its grant decision
- Branch members can also **invite** an active user to take relief duty for a specific future day; accepting writes the day grant immediately. Any active branch member may **revoke** an accepted future duty (#374) — status flips to REVOKED and the day grant is removed atomically; the invitee hears it explicitly (branch+date named), the whole branch hears it too, reminders stop naturally, and the same person may be invited again. Revocation locks once the invitee clocks in at that branch day (the same rule as request retraction); past/REMITTED days cannot be revoked
- **Multiple relief workers may hold edit access at one branch on the same day** — grants do not compete
- The whole branch hears every request and outcome via notifications with explicit **Grant / Deny** choices
- **Relief notification events** (#358): request raised, request granted/denied (naming who acted), invite accepted/declined, and accepted-invite revoked (#374) are broadcast to **every active member of the branch** — clocked in or not — and outcome notices also reach the requester; request cancellation is silent (invite revocation is not — the accept was broadcast). Same-day messages say "today", future ones name the date.
- An unanswered request announces its own **expiry**: when its Branch Day ends, a notice goes to everyone who received the original ping (the request itself stays pending — the day-state is the expiry)
- Tapping a relief notification opens the dashboard scoped to that branch **and date**; appointment reminders keep opening the session
- Once granted, edit access is active until **04:00 AM Asia/Manila** of the following day and is scoped to that one day only
- If relief duty spans multiple days, a new access request must be made each day
- Edit access allows: signing in clients for sessions, adding practitioners to sessions, adding product sales, and receiving commission pool splits

### Compensation

- A relief user's compensation for that day is deducted from the branch where they performed duty, not their home branch
- This applies to both Practitioners and Coordinators on relief duty
- Each user can only source compensation from one branch per day (`unique(user_id, paying_branch_day_id)`)

---

## Clients

- A client is a global record shared across all branches
- **One active session rule:** A client can only have one active session (`PENDING` or `COMPLETED` and not voided) across all branches at any given time.
- Each client has a running log of all their sessions across all branches
- Clients book sessions or walk in — treated identically once the session starts
- A client can request a specific practitioner; if unavailable, any available practitioner is assigned
- Clients can receive discounts or free sessions — remarks are added (e.g. "discount, mcgi")
- Discounted and free sessions still count toward the client's global session history
- After a discounted or free session, the client's default pricing tier for their next session is SUBSEQUENT; this can be manually overridden at session creation

### Client Fields

| Field | Notes |
|-------|-------|
| Name | Required |
| Phone | Cellphone number |
| Address | General location e.g. "Las Pinas", "Bacoor, Cavite" |
| Gender | |
| Age | |
| Blood pressure | |
| Medical conditions | Free text for conditions not covered by the waiver |

### Privacy and Cleanup

- **Soft-Delete/Anonymization:** Clients can be anonymized upon request or for duplicate cleanup.
- Names, phone, address, conditions, and BP fields are nullified.
- Gender and age are retained for aggregate reporting.
- Anonymized records are marked with `deleted_at` and a canonical name marker.
- Audit redaction on anonymization (#524 — the single exception to audit-payload immutability): retained client audit payloads keep event identity (actor, timestamp, action, record, changed-field keys) while identifying first/last-name values become `[redacted]`; uniform `null`s keep their shape. Financial snapshots and demographic aggregates are never rewritten.
- Session free-text scrub on anonymization (#908): operator-entered `session.remarks`/`otherConcerns` and `session_practitioner.remarks` for the anonymized client's sessions are nulled live and rewritten to `[redacted]` in retained session audit payloads (same no-new-events precedent as #524); the dashboard list, bearer session detail, and audit history then serve the scrubbed state. Session prices, status, and financial snapshots are retained, never rewritten.
- Void-reason scrub on anonymization (#909): operator-entered `session_void.voidReason`/`unvoidedReason` for the anonymized client's sessions become `[redacted]` live (`void_reason` is NOT NULL and the unvoid triple is CHECK-bound, so null is impossible; never-unvoided rows keep their null `unvoidedReason` shape) and in retained void audit payloads, and the duplicated `audit_log.reason` on those void rows becomes `[redacted]` — same no-new-events precedent as #524. REMITTED-day reasons on non-void tables are unchanged (pre-existing #524 blind spot, still open).
- Practitioner/concern stickiness on anonymization (#910): practitioner and concern commands take the client row lock first (the #509 client-first order), so a concurrent write serializes with anonymize instead of landing inside its census-to-redaction window; every practitioner/concern write on an anonymized client's sessions is rejected with 409 (the session-create and PENDING-reopen 409 precedent) instead of reintroducing the scrubbed free text. Reads stay open. Concern labels are shared catalog taxonomy, not per-client data: promotion moves only generic labels there (never client-identifying text), per-client scrubbing never rewrites shared `concern` rows or their audit payloads, and promotion on an anonymized client's sessions is rejected with the same 409.

---

## Sessions

A session records one visit by a client at a branch.

### Session Type

Session type is auto-assigned based on the client's **non-medical-mission session history** across all branches. Medical mission sessions are always free and never count toward a client's history. Voided sessions do not count toward history.

| Type | Condition |
|------|-----------|
| `REGULAR` | First non-medical-mission visit anywhere, at a clinic branch |
| `PROVINCIAL_FIRST` | First non-medical-mission visit anywhere, at a provincial tour branch |
| `SECOND_SESSION` | Exactly one prior non-medical-mission session |
| `SUBSEQUENT` | Two or more prior non-medical-mission sessions |
| `MEDICAL_MISSION` | Any visit at a medical mission branch — always free (₱0), never counted toward history |

Session type is never manually changed.

### Base Rates

Base rates are per session type and per branch, editable by Coordinators.

| Type | Default Base Rate |
|------|-----------|
| Regular | ₱2,500 |
| 2nd Session | ₱2,000 |
| Subsequent | ₱1,500 |
| Provincial (first session) | ₱3,500 |
| Medical Mission | ₱0 (always) |

### Pricing

- Final price defaults to the base rate for the session type
- Final price can be overridden per session by the practitioner
- Prices are typically multiples of ₱500 but any amount down to ₱0 is allowed
- Session type and final price are always two independent fields — type does not change when price is overridden
- A remark field captures the reason for any discount or override

### Concerns and Illnesses

- A waiver with checkboxes of common concerns is used during intake
- Multiple concerns can be selected per session
- An "other" free text field covers concerns not on the waiver
- Coordinators can promote "Other" text to a structured concern after matching and review

### Multiple Practitioners

- Multiple practitioners can work on one client in a single session
- Each practitioner adds themselves to the session when they begin working
- Remarks can be added per practitioner
- Display order within a session is by slot order

### Session Status

- Completed session status is immutable through status edits; Coordinators use session void/unvoid for corrections
- Booked sessions can be marked as no-show or cancelled
- Walk-in sessions cannot be marked as no-show or cancelled
- Next appointment date is approximate — the Coordinator is notified 2 days before a scheduled next appointment
- Clients who do not follow through on a booked appointment are marked no-show

### Session Voiding

- Only assigned Coordinators can void a session — sessions are never hard-deleted
- A void reason is required (free text)
- Voided sessions remain visible in the record with a clear visual indicator and are excluded from all financial calculations
- Voiding is covered by the audit trail
- Assigned coordinators can un-void a session if it was done in error
- Voided sessions are visible to all roles by default and can be hidden as a quality-of-life filter

---

## Products and Inventory

### Product Structure

Products are organized in two levels:

```
Category (e.g. Essential Oil, Biomekaniks Infuser, Magnesium Spray)
  └── Product (e.g. Big Roll On, Big Sprayer, Small Sprayer, Potassium, MagSpray)
```

Products within the same category are distinct items, each with their own price, commission, and stock. There are no sub-variants.

Only Coordinators and Owners can add or edit products and update stock levels. `product.is_active` acts as a global master switch; inactive products are hidden from all branches.

| Field | Notes |
|-------|-------|
| Name | |
| Category | Exactly one per product |
| Unit price | The base selling price |
| Commission amount | A flat bonus amount on top of unit price (can be ₱0). The customer pays unit price + commission. |
| Active | Products can be deactivated rather than deleted |

### Inventory

Stock is tracked per branch. Each branch holds its own stock levels for each product. A branch's product listing is determined by the existence of a record in `branch_inventory`.

The inventory sheet tracks four values per product:

| Column | Meaning |
|--------|---------|
| Available | Units currently on shelf and sellable (live count) |
| Stock | Total units received at this branch (baseline) |
| Sales | Units sold |
| Tester / Sample | Units set aside as non-sellable testers or samples |

Available is a derived value: `Available = Stock − Sales − Tester − Sample − Missing`.

Low-stock alerts notify the Coordinator when available stock hits zero or near-zero (highlighted in the UI).

**Special stock movements tracked separately from sales:**

| Movement Type | Description |
|---------------|-------------|
| Tester | Unit set aside for demonstration — not sold |
| Sample | Unit given as a free sample — not sold |
| Missing | Unit unaccounted for; quantity and mandatory reason (notes) recorded |
| Restock | New stock added to branch |
| Adjustment | Manual correction |

- When a client buys a product during a session, available stock automatically decreases and a Sale movement is recorded
- The price recorded on the sale is the price at the time of purchase
- Product sales can be retroactively added to a previous day's session record, subject to the day state machine
- Any practitioner can deduct stock for tester/sample use

### Out-of-Session Product Sales

Clients may return to purchase products outside of a booked session. These sales are recorded with `session_id = NULL` and linked to the client record. If the buyer is genuinely unidentifiable (not in the system), an anonymous sale is permitted — but this should be rare. If the client is in the system, always link them.

### Product Commission

- The commission amount is a flat value added on top of the unit price — the customer pays price + commission
- All commission collected across all product sales for the day is pooled
- The pool is split among all practitioners and coordinators who were clocked in at the time of the sale (`sold_at` window). Sales during lunch breaks are counted.
- The split is high-precision in the DB; the UI truncates to 2 decimal places for the cash handout.
- Recovery flow: Users can be manually included/excluded for specific product sales via a `commission_manual_inclusion` entry.
- Product commissions are treated as tips or bonuses — separate from regular compensation and not subject to remittance

---

## Finance and Compensation

### Compensation

- Compensation is assigned per day, per practitioner, by Owner or Coordinator after viewing daily sales
- Compensation is typically non-zero. The database permits zero for edge cases.
- Compensation varies per practitioner and is decided by Owner or Coordinator
- Relief duty compensation is deducted from the branch where duty was performed, not the home branch
- The paying branch day is always at the same branch as the work branch day — cross-branch paying is not a legitimate flow and is rejected as an invalid request

### Allowances

- A separate allowance amount can be assigned per user per day
- Used for medical mission transport allowances (paid by Owner, not subject to remittance)
- Allowances are distinct from compensation and from expenses

### Expenses

- Anyone can log an expense
- All expenses are company expenses — no practitioner shoulders any cost

| Expense Category |
|-----------------|
| Pantry Items |
| Communication / Load |
| Water |
| Transportation |
| Electricity |
| Rental |
| Office Supplies |
| Furniture / Fixtures / Improvements |
| Miscellaneous / Others |

---

## Remittance

There are two independent remittance flows per branch:

| Flow | What it covers |
|------|---------------|
| HEALot remittance | Net session income (gross − compensation − expenses) |
| Product remittance | Product sales revenue (unit price × quantity; not the commission) |

### Rules

- Only the assigned Coordinator(s) can perform remittance.
- **Draft Status:** Multiple coordinators can view and edit draft remittances to avoid double-work.
- A remittance covers the date range from the day after the previous remittance up to the current day.
- **Financial Snapshot:** At submission, an immutable snapshot of gross income, total compensation, and total expenses is saved to freeze the P&L state.
- Product remittance totals are derived directly from snapshotted `remittance_line` amounts.
- Coordinators must note the remittance method: bank transfer or handed to the accountant

### Day State Machine and Edit Access

Each branch day has a status that governs edit permissions. Transition from OPEN to PAST happens lazily at **04:00 AM Asia/Manila**.

| Status | When | Who Can Edit | UX |
|--------|------|-------------|-----|
| OPEN | Current day (until 4 AM next day) | All on-duty users per current access rules | Normal |
| PAST | After 4 AM boundary | Coordinator only | Warning shown |
| REMITTED | Covered by a remittance | Coordinator only | Stricter warning shown |

- Owner has no special edit access to branch financial records.
- The day state machine applies to: sessions, attendance, expenses, compensations, and product sales.

### Record Integrity

- Records are updated in-place. The full change history is preserved in the audit log.
- Audit trail is visible to all roles

---

## Special Events

### Provincial Tour

- Treated as its own standalone branch (`branch_type = PROVINCIAL_TOUR`)
- Session pricing uses the client's global non-medical-mission session history.
- `PROVINCIAL_FIRST` only applies if global history is zero.

### Medical Mission

- A free event — all sessions are `MEDICAL_MISSION` type and are always ₱0
- Owner assigns delegates (Manager role) to manage medical mission attendance. Delegates can also receive transport allowances as practitioners.

---

## Connectivity and Reliability

### Strictly Online Model

The application operates on a **strictly online** basis. Offline-first synchronization is not supported in Phase 2 due to the complexity of real-time commission splits, auto-assigned session types, and remittance constraints.

- **Optimistic UI:** Mutating operations (e.g. logging a session, product sale) reflect the expected result in the UI immediately while the request is in flight.
- **Auto-Retry:** If a connection drops, the client automatically retries the request until a definitive success or failure is received.
- **Idempotency:** To prevent duplicate records during retries, every mutating request includes a client-generated UUID as its primary identifier. The server ignores subsequent requests with the same ID.
- **Safety Net:** Users see a clear "Saving..." or "Retrying..." state until the server acknowledges the write.

---

## Exports

- Daily sales
- Monthly summary
- All-time summary
- Provincial tour reports
- Medical mission reports
