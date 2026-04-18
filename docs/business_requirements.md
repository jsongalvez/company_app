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

### Roles

| Role | Description |
|------|-------------|
| `SUPERUSER` | Secret developer/owner god mode. Full access to everything. |
| `ADMIN` | Owner. Edit access: home branches + checked-in branch that day. View-only: all branches. Can manage all users. Can work on clients. No special edit rights over branch financial records — Coordinator is the sole owner of PAST and REMITTED record edits. |
| `SPECIAL_COORDINATOR` | A superset of COORDINATOR — all coordinator permissions apply, plus: assigns home branches to all users directly, and manages medical mission delegation. Can work on clients. |
| `COORDINATOR` | Handles finance and remittance for assigned branch(es). Views assigned branches only. Sole editor of PAST and REMITTED records for their branch. Can work on clients. |
| `PRACTITIONER` | Logs sessions, views clients, manages inventory. Views all home branches, plus any branch checked into that day. |
| `ACCOUNTANT` | Read-only. Views sales of all branches. |
| `VIEWER` | Read-only. Future use. |

A user has exactly one role. SPECIAL_COORDINATOR is not a separate role stack — it is a role that inherits all COORDINATOR permissions and adds its own on top. There is no TEMPORARY role — relief access is a behavioral state managed by `grant_relief_access`, not a permanent identity.

---

## Branches

- A branch is either a physical clinic or a periodic off-site location (e.g. one practitioner visits every two weeks)
- Each branch can have one or more assigned Coordinators — set by Admin or SPECIAL_COORDINATOR, permanent until changed. No hard limit on the number of coordinators per branch.
- A Coordinator can be assigned to multiple branches and does not need to be physically present
- For solo or periodic branches, the practitioner hands over money to the Coordinator remotely
- A practitioner can be assigned to more than one home branch simultaneously
- Home branch assignment is performed directly by Admin or SPECIAL_COORDINATOR — there is no request flow from the practitioner

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
- A user in their home branch can set their own slot number; conflicts are resolved manually by Admin or SPECIAL_COORDINATOR
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

This is a query-time filter, not a data deletion. No record is ever hidden — only the access window changes.

---

## Attendance & Presence

- A practitioner taps a button when they first open the app for the day to indicate they are present at a selected branch
- Practitioners can also mark other members of their home branch as present or absent
- A Coordinator can also mark practitioners present at their branch
- Attendance determines:
    - Which branch's data the user can access that day (in addition to their home branch(es))
    - Whether they qualify for product commission splits that day
    - Who appears in the daily compensation assignment list
- Data can be viewed outside working hours — there is no time-bounded access cutoff

---

## Relief Duty

Relief duty applies when any user — Practitioner or Coordinator — checks into a branch that is not one of their home branches.

### Access

- On check-in to a non-home branch, the user automatically has **view-only access** to all branch data for that day
- To gain edit access, the relief user selects a specific currently checked-in user at that branch and sends an access request
- The selected user receives an in-app notification with an explicit **Grant / Deny** choice
- Any currently checked-in user at the branch can grant access — Coordinator presence is not required
- Once granted, edit access is active for the rest of that calendar day only
- If relief duty spans multiple days, a new access request must be made each day
- Edit access allows: signing in clients for sessions, adding practitioners to sessions, adding product sales, and receiving commission pool splits

### Compensation

- A relief user's compensation for that day is deducted from the branch where they performed duty, not their home branch
- This applies to both Practitioners and Coordinators on relief duty

---

## Clients

- A client is a global record shared across all branches
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

---

## Sessions

A session records one visit by a client at a branch.

### Session Type

Session type is auto-assigned based on the client's global session history across all branches. On medical mission branches, all sessions are always assigned `MEDICAL_MISSION` regardless of the client's history.

| Type | Condition |
|------|-----------|
| `REGULAR` | First visit anywhere (non-medical-mission) |
| `SECOND_SESSION` | Second visit (non-medical-mission) |
| `SUBSEQUENT` | Third visit and beyond (non-medical-mission) |
| `PROVINCIAL_FIRST` | First visit ever, at a provincial tour branch |
| `MEDICAL_MISSION` | Any visit at a medical mission branch — always free (₱0) |

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

### Multiple Practitioners

- Multiple practitioners can work on one client in a single session
- Each practitioner adds themselves to the session when they begin working
- Remarks can be added per practitioner
- Display order within a session is by slot order

### Session Status

- Completed sessions are always editable by Coordinators (with appropriate day-state warnings — see Day State Machine)
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

Only Coordinators and Admins can add or edit products and update stock levels.

| Field | Notes |
|-------|-------|
| Name | |
| Category | Exactly one per product |
| Unit price | The base selling price |
| Commission amount | A flat bonus amount on top of unit price (can be ₱0). The customer pays unit price + commission. |
| Active | Products can be deactivated rather than deleted |

### Inventory

Stock is tracked per branch. Each branch holds its own stock levels for each product.

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
| Missing | Unit unaccounted for; quantity and optional reason recorded |
| Restock | New stock added to branch |
| Adjustment | Manual correction |

- When a client buys a product during a session, available stock automatically decreases and a Sale movement is recorded
- The price recorded on the sale is the price at the time of purchase
- Product sales can be retroactively added to a previous day's session record, subject to the day state machine
- Any practitioner can deduct stock for tester/sample use

### Product Commission

- The commission amount is a flat value added on top of the unit price — the customer pays price + commission
- All commission collected across all product sales for the day is pooled
- The pool is split evenly among all practitioners and coordinators who were present (attended) that day, regardless of when they arrived or left
- The per-person split is recalculated live as each product is sold and as people check in throughout the day
- The split is displayed to the Coordinator at end of day for cash handout — no confirmation step required
- Product commissions are treated as tips or bonuses — separate from regular compensation and not subject to remittance

---

## Finance and Compensation

### Compensation

- Compensation is assigned per day, per practitioner, by Admin or Coordinator after viewing daily sales
- Compensation is never zero — even if a practitioner had no sessions that day, some amount is always provided
- Compensation varies per practitioner and is decided by Admin or Coordinator
- Relief duty compensation is deducted from the branch where duty was performed, not the home branch

### Allowances

- A separate allowance amount can be assigned per user per day
- Used for medical mission transport allowances (paid by Admin, not subject to remittance)
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

### Daily Financials

- **Gross income** = total final price of all completed, non-voided sessions that day
- **Net income** = gross income − compensation expenses − other expenses
- Product sales revenue is tracked separately and does not factor into gross or net income
- Product commissions are not included in any remittance flow

---

## Remittance

There are two independent remittance flows per branch:

| Flow | What it covers |
|------|---------------|
| HEALot remittance | Net session income (gross − compensation − expenses) |
| Product remittance | Product sales revenue (unit price × quantity; not the commission) |

### Rules

- At most one HEALot remittance and one product remittance per branch per day
- Only the assigned Coordinator(s) can perform remittance — cannot be delegated
- A remittance covers the date range from the day after the previous remittance up to the current day
- The remittance record shows the total amount remitted, the date range covered, and a daily breakdown for each day in the range
- Coordinators must note the remittance method: bank transfer or handed to the accountant

### Day State Machine and Edit Access

Each branch day has a status that governs edit permissions:

| Status | When | Who Can Edit | UX |
|--------|------|-------------|-----|
| OPEN | The current calendar day | All on-duty users per current access rules | Normal |
| PAST | A previous day not yet remitted | Coordinator only | Warning shown |
| REMITTED | Covered by a remittance | Coordinator only | Stricter warning shown |

- Admin has no special edit access to branch financial records — the Coordinator is the sole owner of PAST and REMITTED record edits
- The day state machine applies to: sessions, attendance, expenses, compensations, and product sales

### Record Integrity

- No record is ever mutated in the database
- An edit creates a new version that overrides the display; the original stays pristine underneath
- Full audit trail is maintained: who changed it, when, and what the previous value was
- Audit trail is visible to all roles

### Visibility

| Role | Can see |
|------|---------|
| Admin | All branches |
| Accountant | All branches |
| Coordinator | Assigned branches only |
| Practitioner | Home branch(es) only + checked-in branch(es) that day |

---

## Special Events

### Provincial Tour

- Practitioners travel to an off-site location for one or more consecutive days
- Treated as its own standalone branch (`branch_type = PROVINCIAL_TOUR`) with its own history — income and compensation do not roll into any regular branch
- Practitioners rotate — membership is not fixed per tour
- Session pricing uses the client's global session history (same auto-assignment logic as regular branches)
- Practitioners receive daily compensation using the same Admin/Coordinator assignment flow
- Only Admins can view provincial tour reports

### Medical Mission

- A free event — all sessions are `MEDICAL_MISSION` type and are always ₱0
- Always its own branch record (`branch_type = MEDICAL_MISSION`), even if physically hosted at an existing clinic location
- Admin assigns one or more delegates (SPECIAL_COORDINATOR) to manage medical mission attendance
- The delegate(s) act as Coordinators for medical missions — can assign practitioners to a mission
- Practitioners receive a transport allowance (not compensation) provided by Admin
- All roles can view medical mission reports

---

## Client Search

- Typeahead search as the user types, debounced ~300ms before firing the request
- Fuzzy matching to handle typos (e.g. "Jhn" finds "John")
- Search supports both client name (fuzzy) and phone number
- Implementation: PostgreSQL `pg_trgm` + `ILIKE`

---

## Daily Sales and Reporting

- Daily sales shows all sessions for the day, visible to practitioners of the same branch only
- All roles can view the full historical record, not just the current day

### Monthly Summary Columns

| Column | Notes |
|--------|-------|
| # | Row number |
| Date | |
| Day | Day of week |
| Gross Income | |
| Compensation Expense | |
| Other Expense | |
| Total Expenses | Compensation + Other |
| Net Income | |
| No. of Clients | |
| Practitioner | Ordered by branch slot number (slot 1 first), then relief practitioners after |
| Coordinator | |

A totals row appears at the bottom for all numeric columns.

---

## Access Control Summary

| Role | Branch access | Special access |
|------|--------------|----------------|
| Superuser | Everything | Secret god mode |
| Admin | Edit: home branches + checked-in branch that day. View: all branches. | Can work on clients. No special edit rights over branch financial records. |
| Special Coordinator | Assigned branches + checked-in branch that day | Assigns home branches to all users. Manages medical mission delegates. All COORDINATOR permissions. |
| Coordinator | Assigned branches only | Finance and remittance. Sole editor of PAST and REMITTED records for their branch. |
| Practitioner | All home branches + checked-in branch(es) that day | |
| Accountant | All branches | Read-only |
| Viewer | TBD | Read-only, future use |

Resigned/inactive users appear as-is on all historical records — their data is never altered or hidden.

---

## Exports

- Daily sales
- Monthly summary
- All-time summary
- Provincial tour reports
- Medical mission reports
