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
| `ADMIN` | Owner. Edit access: home branches + checked-in branch that day. View-only: all branches. Can manage all users. Can work on clients. |
| `SPECIAL_COORDINATOR` | Permanent role assigned by Admin. Assigns home branches to all users directly. Manages medical mission delegation. Can work on clients. |
| `COORDINATOR` | Handles finance and remittance for assigned branch(es). Views assigned branches only. Can work on clients. |
| `PRACTITIONER` | Logs sessions, views clients, manages inventory. Views all home branches, plus any branch checked into that day. |
| `ACCOUNTANT` | Read-only. Views sales of all branches. |
| `TEMPORARY` | Relief practitioner. Always sees home branch(es). Also sees daily sales of any branch checked into that day. |
| `VIEWER` | Read-only. Future use. |

---

## Branches

- A branch is either a physical clinic or a periodic off-site location (e.g. one practitioner visits every two weeks)
- Each branch has exactly one assigned Coordinator — set by Admin or SPECIAL_COORDINATOR, permanent until changed
- A Coordinator can be assigned to multiple branches and does not need to be physically present
- For solo or periodic branches, the practitioner hands over money to the Coordinator remotely
- A practitioner can be assigned to more than one home branch simultaneously — for example, a practitioner who splits their time between two clinics can be considered "home" at both
- Home branch assignment is performed directly by Admin or SPECIAL_COORDINATOR — there is no request flow from the practitioner

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
- Once granted, edit access is active for the rest of that calendar day
- Edit access allows: signing in clients for sessions, adding practitioners to sessions, adding product sales, and receiving commission pool splits

### Compensation

- A relief user's compensation for that day is deducted from the branch where they performed duty, not their home branch
- This applies to both Practitioners and Coordinators on relief duty

---

## Seniority
TODO: Pain in the ass, need to remove this somehow (maybe have it be rearrangeable)
Seniority is a system-maintained designation stored as a tier on each user. It never changes once assigned — OG status is a founding-era designation. Seniority determines ordering in reports and influences compensation level; Admin still manually assigns compensation amounts.

| Tier | Who |
|------|-----|
| 1 — CEO | The Admin/owner. Always most senior. |
| 2 — OG | Founding practitioners. Assigned at account creation. |
| 3 — NON_OG | All other practitioners. Default for new hires. |

Seniority determines the ordering of practitioners in the monthly summary and within a session's practitioner list: OG members appear before NON_OG members. Within each tier, ordering is by who worked on the client first in that session.

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

Session type is auto-assigned based on the client's global session history across all branches and is never manually changed:

| Type | Condition |
|------|-----------|
| Regular | First visit anywhere |
| 2nd Session | Second visit |
| Subsequent | Third visit and beyond |
| Provincial (first session) | First visit ever, at a provincial tour |

### Base Rates

Base rates are per session type and are editable by Coordinators. Defaults:

| Type | Base Rate |
|------|-----------|
| Regular | ₱2,500 |
| 2nd Session | ₱2,000 |
| Subsequent | ₱1,500 |
| Provincial (first session) | ₱3,500 |

### Pricing

- Final price defaults to the base rate for the session type
- Final price can be overridden per session by the practitioner
- Prices are typically multiples of ₱500 but any amount down to ₱0 is allowed
- Session type and final price are always two independent fields — type does not change when price is overridden

### Concerns and Illnesses

- A waiver with checkboxes of common concerns is used during intake
- Multiple concerns can be selected per session
- An "other" free text field covers concerns not on the waiver

### Multiple Practitioners

- Multiple practitioners can work on one client in a single session
- Each practitioner adds themselves to the session when they begin working
- Remarks can be added per practitioner

### Session Status

- Completed sessions are always editable by Coordinators (with appropriate day-state warnings — see Day State Machine)
- Booked sessions can be marked as no-show or cancelled
- Walk-in sessions cannot be marked as no-show or cancelled
- Next appointment date is approximate — clients may return earlier or later than scheduled; clients who do not follow through are marked no-show
- The Coordinator is notified 2 days before a scheduled next appointment

### Session Voiding

- Only Coordinators can void a session — sessions are never hard-deleted
- A void reason is required (free text)
- Voided sessions remain visible in the record with a clear visual indicator and are excluded from all financial calculations
- Voiding is a versioned action covered by the audit trail
- An Admin can un-void a session if it was done in error
- Voided sessions are visible to all roles by default and can be hidden as a quality-of-life filter

---

## Products and Inventory

### Products
TODO: need to refine this, products have subcategories like Product Essential Oil, subcategories: big spray, small spray
- Products are grouped by exactly one category (e.g. Essential Oil, Biomekaniks Infuser, Magnesium Spray, K-ION)
- Only Coordinators and Admins can add or edit products and update stock levels

| Field | Notes |
|-------|-------|
| Name | |
| Category | Exactly one per product |
| Unit price | Can change over time |
| Commission amount | Flat bonus on top of unit price; customer pays price + commission |

### Inventory

- Stock is tracked per branch
- When a client buys a product during a session, stock automatically decreases
- The price recorded on the session is the price at the time of purchase
- Product sales can be retroactively added to a previous day's session record, subject to the day state machine
- Any practitioner can deduct stock for tester samples
- Inventory can be marked as missing — quantity and an optional reason are recorded
- Inventory tracking covers: available stock, total stock, sales quantity
- Low-stock alerts notify the Coordinator

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
- Admin views the sessions for the day and who is on duty, then assigns a value per practitioner
- Compensation is never zero — even if a practitioner had no sessions that day, some amount is always provided
- Compensation varies per practitioner and is decided based on daily sales and seniority

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

- **Gross income** = total final price of all completed sessions that day
- **Net income** = gross income − compensation expenses − other expenses
- Product sales revenue is tracked separately and does not factor into gross or net income

---

## Remittance

There are two independent remittance flows per branch:

| Flow | What it covers |
|------|---------------|
| HEALot remittance | Net session income (gross − compensation − expenses) |
| Product remittance | Product sales revenue (unit price × quantity; not the commission) |

### Rules

- At most one HEALot remittance and one product remittance per branch per day
- Only the assigned Coordinator can perform remittance — cannot be delegated
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

- Admin has no special edit access to branch financial records — the Coordinator is the sole owner of past and remitted record edits
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
| Practitioner | Home branch(es) only |

---

## Special Events

### Provincial Tour

- Practitioners travel to an off-site location for one or more consecutive days
- Treated as its own standalone branch with its own history — income and compensation do not roll into any regular branch
- Practitioners rotate — membership is not fixed per tour
- Session pricing uses the client's global session history (same auto-assignment logic as regular branches)
- Practitioners receive daily compensation using the same Admin/Coordinator assignment flow
- Only Admins can view provincial tour reports

### Medical Mission

- A free version of a provincial tour — all sessions are free (gross income = ₱0)
- Admin assigns one global delegate (the SPECIAL_COORDINATOR) to manage medical mission attendance; this is ongoing until Admin changes it
- The delegate acts as the Coordinator for medical missions — can assign practitioners to a mission
- Only one active delegate at a time; the previous delegate loses access immediately upon reassignment
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
| Practitioner | Ordered by seniority tier, then by who worked the client first within tier |
| Coordinator | |

A totals row appears at the bottom for all numeric columns.

---

## Access Control Summary
TODO: Special coordinator is an extension to coordinator, they act as a coordinator of a branch in addition to their added responsibilities
| Role | Branch access | Special access |
|------|--------------|----------------|
| Admin | Edit: home branches + checked-in branch that day. View: all branches. | Can work on clients. No special edit rights over branch financial records. |
| Special Coordinator | Assigned branches + checked-in branch that day | Assigns home branches to all users. Medical mission delegation. |
| Coordinator | Assigned branches only | Finance and remittance. Sole editor of PAST and REMITTED records for their branch. |
| Practitioner | All home branches + checked-in branch(es) that day | |
| Temporary | All home branches + checked-in branch(es) that day | Daily sales view only for relief branches unless edit access is granted |
| Accountant | All branches | Read-only |
| Viewer | TBD | Read-only, future use |

Resigned users appear as-is on all historical records — their data is never altered or hidden.

---

## Exports

- Daily sales
- Monthly summary
- All-time summary
- Provincial tour reports
- Medical mission reports
