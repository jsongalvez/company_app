# Business Requirements — Phase 2 Planning

This document captures what we know about the business so far, and what still needs to be confirmed before the database schema can be designed.

---

## What We Know

### The Business
- Physical therapy / wellness practitioners work on clients on a per-session basis
- Multiple branches in different locations
- All branches share one cloud-hosted backend and one database
- Each branch has its own inventory stock

### Terminology
- **Practitioner** — formerly "employee"; the person who works on clients
- **Coordinator** — formerly "manager"; handles finance, remittance, and branch administration

### Users of the App
- Practitioners use the app primarily on a laptop (Windows), with Android and iOS as secondary
- All practitioners use mobile hotspots — no shared office network
- Roles: `ADMIN`, `COORDINATOR`, `PRACTITIONER`, `VIEWER`, `ACCOUNTANT`, `TEMPORARY`
    - `ADMIN` — owner, full access, manages all users, views all branches, can work on clients
    - `COORDINATOR` — handles finance and remittance for assigned branch(es), registers users, views assigned branches only
    - `PRACTITIONER` — logs sessions, views clients, manages inventory, views home branch only
    - `VIEWER` — read-only, future use
    - `ACCOUNTANT` — read-only, views sales of all branches
    - `TEMPORARY` (name pending) — relief practitioner, always sees home branch; also sees daily sales of any branch they check into that day

### Branches
- A branch is either a physical clinic or a periodic off-site location (e.g. one practitioner visits every two weeks)
- Each branch has exactly one assigned Coordinator — set by Admin, permanent until changed
- A Coordinator can be assigned to multiple branches and does not need to be physically present
- For solo/periodic branches, the practitioner hands over money to the Coordinator remotely
- Practitioners belong to exactly one home branch — no exceptions
- A practitioner can request a home branch change; Admin or their assigned Coordinator can approve or deny; they remain on their current home branch until approved

### Attendance & Presence
- A practitioner taps a button when they first open the app for the day to indicate they are present at a selected branch
- A Coordinator can also mark practitioners present at their branch
- Attendance determines:
    - Which branch's data the practitioner can access that day (in addition to their home branch)
    - Whether they qualify for product commission splits that day
    - Who appears in the daily compensation assignment list
- Data can be viewed outside working hours — there is no time-bounded access cutoff

### Clients
- Clients book sessions or walk in — treated identically once the session starts
- A client is shared across branches (global client record)
- Each client has a running log of all their sessions across all branches
- Client fields: name, cellphone number, address (general location e.g. "Las Pinas", "Bacoor, Cavite"), gender, age, blood pressure, other medical conditions (free text)
- A client can request a specific practitioner; if unavailable, any available practitioner is assigned
- Clients can receive discounts or free sessions — remarks are added (e.g. "discount, mcgi")
- Discounted/free sessions still count toward the client's global session history

### Sessions
- A session records one visit by a client
- Session fields: date, branch, client, session type, final price, practitioner(s), concerns/illnesses, next appointment date, products bought (with price at time of purchase), remarks
- Multiple practitioners can work on one client in a single session — remarks can be added
- Session type is auto-assigned based on the client's global session history across all branches:
    - First visit anywhere = **Regular**
    - Second visit = **2nd Session**
    - Third visit and beyond = **Subsequent**
    - First visit at a provincial tour (no prior history) = **Provincial (first session)**
- Base rates per session type (editable by Coordinator):
    - Regular — ₱2500
    - 2nd Session — ₱2000
    - Subsequent — ₱1500
    - Provincial (first session) — ₱3500
- Final price can be overridden per session by the practitioner on a case-by-case basis
- Prices are typically multiples of ₱500 but any amount down to ₱0 is allowed
- Concerns/illnesses: checkboxes from a waiver with common concerns + "other" free text
- Sessions can be edited after submission by Practitioner, Coordinator, or Admin
- Booked sessions can be marked as **no-show** or **cancelled**
- Walk-in sessions cannot be no-show/cancelled
- Next appointment is approximate — clients who don't follow through are marked **no-show**

### Seniority & Ordering
- Seniority is a manually maintained list controlled by Admin
- Tiers:
    1. CEO (Admin) — most senior
    2. OG members — founding practitioners, listed on the seniority list
    3. Non-OG members — not on the seniority list
- Within a session, practitioners are ordered by: OG members first, non-OG members after
- Within each tier, ordering is by who worked on the client first (within that session)
- This ordering is used in the monthly summary employee column

### Products & Inventory
- Products are grouped by category (e.g. Essential Oil, Biomekaniks Infuser, Magnesium Spray, K-ION) — exactly one category per product
- Product fields: name, unit price, commission amount (flat value added on top; customer pays price + commission)
- Commission from product sales is spread evenly among all practitioners and coordinators who were present (attended) that day
- Product commissions are treated as tips/bonuses — separate from regular compensation
- Stock is per-branch
- When a client buys a product during a session, stock automatically decreases
- The recorded price is the price at the time of purchase
- Products can be used as tester samples — any practitioner can manually deduct from stock
- Inventory can be marked as missing (quantity + optional reason)
- Only Coordinators and Admins can add or edit products and update stock levels
- Inventory tracking includes: available stock, total stock, sales quantity
- Low-stock alerts notify the Coordinator

### Finance & Compensation
- Compensation is per day, assigned manually by Admin or Coordinator after viewing daily sales
- Admin views sessions for the day + who is on duty, then assigns a compensation value per practitioner
- Compensation is never zero — even if a practitioner had no sessions that day, some amount is always provided
- Compensation varies per practitioner — decided by Admin based on daily sales
- Gross income = total income from all client sessions that day
- Net income = gross income − compensation expenses − other expenses
- All expenses are company expenses (no practitioner shoulders any expense)
- Expense types:
    - Pantry Items
    - Communication/Load
    - Water
    - Transportation
    - Electricity
    - Rental
    - Office Supplies
    - Furnitures/Fixtures/Improvements
    - Miscellaneous / Others
- Anyone can log an expense
- Product commissions are separate from session income — treated as bonus/tips

### Remittance
- Remittance = the net income sent to the bank after deducting all expenses and compensation
- The assigned Coordinator for a branch handles remittance — at most one remittance per day per branch
- A remittance covers all net income from the day after the previous remittance up to the current day
- The remittance record shows: total amount remitted, the date range covered, and the daily breakdown for each day in the range
- Only a Coordinator can perform remittance — cannot be delegated
- Remittance history is visible to all users:
    - Admin: all branches
    - Coordinator/Practitioner: own branch only
    - Accountant: all branches

### Special Events

#### Provincial Tour
- Practitioners travel to an off-site location for one day (or several consecutive days)
- Treated as its own standalone "branch" with its own history — not tied to any regular branch
- Income and compensation do not roll into any regular branch's summary
- Practitioners rotate — membership is not fixed
- Session pricing follows the client's global history (same auto-assignment logic as regular branches)
- Provincial (first session) base rate = ₱3500; price is overridable per client as usual
- Employees receive daily compensation on provincial tour days using the same Admin/Coordinator assignment flow
- Only Admins can view provincial tour reports

#### Medical Mission
- A free version of a provincial tour — all sessions are free (gross income = ₱0)
- Admin assigns one global delegate to manage medical mission attendance; this is ongoing until Admin changes it
- The delegate acts as the Coordinator for medical missions — can assign practitioners to a mission
- Anyone can be made the delegate; when reassigned, the previous delegate loses access immediately
- Only one active delegate at a time; support for multiple delegates may be needed in the future
- Practitioners receive a transport allowance (not compensation) provided by Admin
- All roles can view medical mission reports

### Daily Sales & Reporting
- A list of all sessions for the day — visible to practitioners of the same branch only
- Practitioners cannot see other branches' data
- All roles can view the full history (not just today)
- Monthly summary columns:
    - `#`, `DATE`, `DAY`, `GROSS_INCOME`, `COMPENSATION_EXPENSE`, `OTHER_EXPENSE`, `TOTAL_EXPENSES`, `NET_INCOME`, `No_of_Clients`, `Practitioner` (ordered by seniority tier, then by who worked the client first within tier), `Coordinator`
    - Totals row at the bottom for all numeric columns
    - Practitioner column shows who was on duty that day

### Client Search
- Typeahead search as user types
- Fuzzy matching to handle typos (e.g. "Jhn" finds "John")
- Implementation: PostgreSQL `pg_trgm` + `ILIKE`, debounced ~300ms on frontend

### Access Control Summary
- Admin: full access, all branches, can work on clients
- Coordinator: assigned branches only, handles finance and remittance
- Practitioner: home branch always visible; also sees any branch they check into that day
- Temporary: home branch always visible; also sees daily sales of any branch they check into that day
- Accountant: read-only, all branches
- Resigned users appear as-is on all historical records

### Exports
- Export daily sales
- Export summary reports (monthly, all-time)
- Export provincial tour reports
- Export medical mission reports

---

## Questions for Dad — Round 1 (Answered)

### Clients

1. Is a client shared across branches, or does each branch have their own separate client list?
   **A. Yes, a client can appear in multiple branches.**

2. Does a client have a fixed assigned practitioner, or can any practitioner work on them?
   **A. Can request a specific practitioner, but if unavailable any other practitioner will be assigned.**

3. Can a client have multiple concerns/illnesses logged per session, or just one?
   **A. Their current workflow is that a waiver has checkboxes of common concerns and an "other" for those not covered.**

### Sessions / Appointments

4. When a next appointment is set, who gets notified when it's coming up?
   **A. The Coordinator. 2 days before.**

5. How should the app handle clients who don't follow through on their next appointment?
   **A. Marked no-show. No additional actions.**

6. Can a session be edited after it's been saved, or is it locked once submitted?
   **A. Editable. Practitioner, Coordinator, and Admin.**

7. Is there a concept of a "cancelled" or "no-show" session?
   **A. Booked sessions are marked no-show or cancelled.**

### Products & Inventory

8. When a client buys a product during a session, does stock automatically go down?
   **A. Yes, it automatically goes down.**

9. Can a product's price change over time? Does the session record the price at time of purchase?
   **A. Yes, price can change. The recorded price is at the time of purchase.**

10. Who can add or edit products and update stock levels?
    **A. Coordinators/Admins.**

11. Is there a low-stock alert?
    **A. Yes. Coordinator is notified.**

### Finance & Compensation

12. How is a practitioner's compensation calculated?
    **A. Per day. Admin views branch sales at end of day (sessions + who is on duty) then assigns a value per practitioner. Admin or Coordinator assigns the pay. Remaining income after compensation and expenses is remitted to the bank by the Coordinator. Product commissions are treated as tips/bonuses.**

13. Is the compensation the same for all practitioners?
    **A. Varies. Admin supplies the amount based on daily sales.**

14. What counts as a business expense?
    **A. Anyone can log an expense. Types: Pantry, utilities (water, electricity), office supplies, rent, communication, load, transport, and one-offs. All are company expenses.**

15. What is shown in the monthly summary?
    **A. `#`, `DATE`, `DAY`, `GROSS_INCOME`, `COMPENSATION_EXPENSE`, `OTHER_EXPENSE`, `TOTAL_EXPENSES`, `NET_INCOME`, `No_of_Clients`, `Practitioner` (ranked by seniority), `Coordinator`. Includes totals row.**

### Remittance

16. Is remittance tracked per transaction or as a running balance?
    **A. Remittance is money sent to the bank after calculating net income. Coordinator handles it on their chosen day. Covers net income from the day after the previous remittance up to current day.**

17. Can the Coordinator delegate remittance to someone else?
    **A. No. Only the Coordinator can remit.**

18. Can a practitioner see remittance history?
    **A. Remittance history shown to all. Admin and Accountant: all branches. Coordinator and Practitioner: own branch only.**

### Practitioners & Access

19. Who can register new accounts?
    **A. Admins and Coordinators.**

20. Can a practitioner belong to multiple branches?
    **A. Belongs to one home branch. May temporarily relieve another branch.**

21. When a practitioner is deactivated, what happens to their records?
    **A. Resigned practitioners appear as-is on past records.**

### Daily Sales

22. Can all practitioners see everyone's sales for the day?
    **A. Practitioners can see their own branch's daily sales only.**

23. Can past days be viewed?
    **A. All roles can view the whole history.**

24. Is there a need to print or export?
    **A. Export daily sales and summary reports (monthly, all-time).**

### General

25. Are there any roles not yet covered?
    **A. Accountant (read-only, all branches). Temporary/relief practitioner (name pending).**

26. Is there anything the app absolutely must do that hasn't been covered?
    **A. Nothing else comes to mind yet — maybe during product demo.**

---

## Questions for Dad — Round 2 (Answered)

### Temporary / Relief Practitioners

27. When a relief practitioner is assigned to another branch temporarily, how is that recorded?
    **A. Practitioner taps a button when they first open the app to indicate they are present at a selected branch. The Coordinator of a branch can also mark practitioners present. Data can be viewed outside working hours so there is no time-bounded access cutoff.**

28. Can a relief practitioner still see their home branch data while on relief duty at another branch?
    **A. Yes, always. Additionally there are two special event types: provincial tour (Admin-only report) and medical mission (visible to all roles).**

### Compensation & "Working That Day"

29. How is "working that day" defined for product commission splits?
    **A. Anyone who was present/attended that day counts.**

30. Can a practitioner have zero compensation for a day?
    **A. Never zero — even if no clients that day, some amount is always provided.**

### Remittance Scope

31. Is a remittance broken down per day or recorded as a lump sum?
    **A. Shows the total remitted covering the date range, plus the separate daily totals for each day in the range.**

32. Can there be multiple remittances in a single day?
    **A. At most one per day per branch.**

### Inventory

33. When stock is marked as missing, is a reason recorded?
    **A. Reason can be added as an optional entry.**

34. Who can deduct tester samples from stock?
    **A. Any practitioner.**

35. Can a product belong to more than one category?
    **A. Exactly one category.**

### Sessions

36. Is the session number automatic or manually entered?
    **A. Automatic and global across all branches. First visit anywhere = Regular. Second = 2nd Session. Third and beyond = Subsequent.**

37. Can a single session have multiple practitioners?
    **A. Yes. Remarks can be added.**

### Clients

38. Is the "clinic" field always the branch where the session is logged?
    **A. Clients can visit any branch. Session type is determined by their global history across all branches.**

---

## Questions for Dad — Round 3 (Answered)

### Provincial Tour & Medical Mission

39. Does a provincial tour belong to a specific branch, or is it standalone?
    **A. Standalone — has its own history. Think of it as its own branch where membership rotates and days are sporadic. Income and compensation do not roll into any regular branch.**

40. Do provincial tour and medical mission sessions count toward a client's global session history?
    **A. Yes — existing session history applies. A returning client gets the discounted rate based on their history. Price is always overridable.**

41. Do practitioners receive daily compensation for provincial tour days?
    **A. Yes, same Admin/Coordinator assignment flow as regular branches. Medical missions provide transport allowance only — no compensation.**

42. For medical missions, is the delegation permanent or per-event?
    **A. Ongoing until changed by Admin. The delegate acts as the Coordinator for medical missions. Anyone can be made the delegate. Previous delegate loses access immediately upon reassignment. Only one active delegate at a time — multiple delegate support may be needed in the future.**

43. For medical missions, what is recorded as gross income?
    **A. Zero — all sessions are free.**

### Remittance Across Branches

44. Is coordinator-to-branch assignment permanent or ad hoc?
    **A. Some branches are serviced by one visiting practitioner every two weeks. Whoever is the Coordinator for that branch handles remittance remotely. Branch-Coordinator assignment is set by Admin.**

45. Can two Coordinators both be assigned to the same branch?
    **A. Only one Coordinator per branch.**

### Pricing & Session Types

46. Are session type and final price always two independent fields?
    **A. Yes — type is auto-assigned and does not change. Price starts at the base rate for that type and can be overridden per session.**

47. Does provincial tour pricing use the client's global session count?
    **A. Yes. Returning clients are counted at their existing tier (e.g. already had 1 session = 2nd Session rate = ₱2000). Price is always overridable.**

### Access Control

48. Does checking into another branch replace or add to a practitioner's home branch access?
    **A. Home branch is always visible. Checking in adds access for that day — it does not replace home branch access. Practitioners set their home branch after registering. Home branch change requires a request approved by Admin or assigned Coordinator; practitioner stays on current home branch until approved.**

### Seniority

49. Where does seniority come from?
    **A. A manually maintained list. Tiers: (1) CEO/Admin — most senior, (2) OG members — founding practitioners on the list, (3) Non-OG members — not on the list. Within a session, OG members are listed before non-OG members. Within each tier, order is by who worked on the client first in that session.**
