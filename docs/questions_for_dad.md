# Business Requirements — Phase 2 Planning

This document captures what we know about the business so far, and what still needs to be confirmed before the database schema can be designed.

---

## What We Know

### The Business
- Physical therapy / wellness practitioners work on clients on a per-session basis
- Multiple branches in different locations
- All branches share one cloud-hosted backend and one database
- Each branch has its own inventory stock

### Users of the App
- Employees use the app primarily on a laptop (Windows), with Android and iOS as secondary
- All employees use mobile hotspots — no shared office network
- Roles: `ADMIN`, `MANAGER`, `EMPLOYEE`, `VIEWER`, `ACCOUNTANT`, `TEMPORARY`
    - `ADMIN` — owner, full access, manages employees, views stats of all branches, can work on clients like employees
    - `MANAGER` — handles finance and remittance (possibly for multiple branches), registers employees, views own branch only
    - `EMPLOYEE` — practitioners, log sessions, view clients, manage inventory, views own branch only
    - `VIEWER` — read-only, future use
    - `ACCOUNTANT` — read-only, views sales of all branches
    - `TEMPORARY` (name pending) — relief employee, access determined by daily attendance check-in

### Attendance & Presence
- An employee taps a button when they first open the app for the day to indicate they are present at a selected branch
- A Manager can also mark employees present at their branch
- Attendance determines:
    - Which branch's data the employee can access that day
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
- Discounted/free sessions still count toward the client's session number history

### Sessions
- A session records one visit by a client
- Session fields: date, branch, client, session type, price (adjustable), practitioner(s), concerns/illnesses, next appointment date, products bought (with price at time of purchase), remarks
- Multiple practitioners can work on one client in a single session — remarks can be added
- Session types (base rates, editable by Manager):
    - Regular — ₱2500
    - 2nd Session — ₱2000
    - Subsequent — ₱1500
    - Others — ₱3500
    - Others — ₱2000
- Price is adjustable per session by the practitioner — overrides the base rate
- Prices are typically multiples of ₱500, but any amount down to ₱0 is allowed
- Session number is automatic and global across all branches:
    - First visit anywhere = Regular (1st)
    - Second visit = 2nd Session
    - Third and beyond = Subsequent
- Concerns/illnesses: checkboxes from a waiver with common concerns + "other" free text
- Sessions can be edited after submission by Employee, Manager, or Admin
- Booked sessions can be marked as **no-show** or **cancelled**
- Walk-in sessions cannot be no-show/cancelled
- Next appointment is approximate — clients who don't follow through are marked **no-show**

### Products & Inventory
- Products are grouped by category (e.g. Essential Oil, Biomekaniks Infuser, Magnesium Spray, K-ION) — exactly one category per product
- Product fields: name, unit price, commission amount (flat value added on top; customer pays price + commission)
- Commission from product sales is spread evenly among all employees and managers who were present (attended) that day
- Product commissions are treated as tips/bonuses — separate from regular compensation
- Stock is per-branch
- When a client buys a product during a session, stock automatically decreases
- The recorded price is the price at the time of purchase
- Products can be used as tester samples — any employee can manually deduct from stock
- Inventory can be marked as missing (quantity + optional reason)
- Only managers and admins can add or edit products and update stock levels
- Inventory tracking includes: available stock, total stock, sales quantity
- Low-stock alerts notify the Manager

### Finance & Compensation
- Compensation is per day, assigned manually by Admin or Manager after viewing daily sales
- Admin views sessions for the day + who is on duty, then assigns a compensation value per employee
- Compensation is never zero — even if an employee had no sessions that day, some amount is always provided
- Compensation varies per employee — decided by Admin based on daily sales
- Gross income = total income from all client sessions that day
- Net income = gross income − compensation expenses − other expenses
- All expenses are company expenses (no employee shoulders any expense)
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
- Product commissions are separate from session income — treated as bonus/tips for employees

### Remittance
- Remittance = the net income sent to the bank after deducting all expenses and compensation
- Manager handles remittance on the day of their choosing — at most one remittance per day per branch
- A remittance covers all net income from the day after the previous remittance up to the current day
- The remittance record shows: total amount remitted, the date range covered, and the daily breakdown for each day in the range
- Some managers handle remittances for multiple branches (not just their home branch)
- Only a Manager can perform remittance — cannot be delegated
- Remittance history is visible to all users:
    - Admin: all branches
    - Manager/Employee: own branch only
    - Accountant: all branches

### Special Events
- **Provincial Tour** — practitioners travel to an off-site location for one day; sessions are charged at ₱3500 (Regular), with subsequent sessions reduced by ₱500 each; has its own report; only Admins can view provincial tour reports
- **Medical Mission** — a free version of a provincial tour; Admin can delegate someone to assign practitioners on their behalf for that day; has its own report; all roles can view medical mission reports
- Provincial tour and medical mission daily sales are separate from regular branch sales

### Daily Sales & Reporting
- A list of all sessions for the day — visible to employees of the same branch only
- Employees cannot see other branches' data
- All roles can view the full history (not just today)
- Monthly summary columns:
    - `#`, `DATE`, `DAY`, `GROSS_INCOME`, `COMPENSATION_EXPENSE`, `OTHER_EXPENSE`, `TOTAL_EXPENSES`, `NET_INCOME`, `No_of_Clients`, `Employee` (ranked by seniority), `Coordinator (Manager)`
    - Totals row at the bottom for all numeric columns
    - Employee column shows employees who were on duty that day

### Client Search
- Typeahead search as user types
- Fuzzy matching to handle typos (e.g. "Jhn" finds "John")
- Implementation: PostgreSQL `pg_trgm` + `ILIKE`, debounced ~300ms on frontend

### Access Control Summary
- Admin: full access, all branches, can work on clients
- Manager: own branch + any branches they are assigned to remit for
- Employee/Viewer: own branch only
- Accountant: read-only, all branches
- Temporary: access to branches they check in at that day
- Resigned employees appear as-is on historical records

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
   **A. The Manager. 2 days before.**

5. How should the app handle clients who don't follow through on their next appointment?
   **A. Marked no-show. No additional actions.**

6. Can a session be edited after it's been saved, or is it locked once submitted?
   **A. Editable. Employee, Manager, and Admin.**

7. Is there a concept of a "cancelled" or "no-show" session?
   **A. Booked sessions are marked no-show or cancelled.**

### Products & Inventory

8. When a client buys a product during a session, does stock automatically go down?
   **A. Yes, it automatically goes down.**

9. Can a product's price change over time? Does the session record the price at time of purchase?
   **A. Yes, price can change. The recorded price is at the time of purchase.**

10. Who can add or edit products and update stock levels?
    **A. Managers/admins.**

11. Is there a low-stock alert?
    **A. Yes. Manager is notified.**

### Finance & Compensation

12. How is a practitioner's compensation calculated?
    **A. Per day. Admin views branch sales at end of day (sessions + who is on duty) then assigns a value per employee. Admin or Manager assigns the pay. Remaining income after compensation and expenses is remitted to the bank by the Manager. Product commissions are treated as tips/bonuses.**

13. Is the compensation the same for all practitioners?
    **A. Varies. Admin supplies the amount based on daily sales.**

14. What counts as a business expense?
    **A. Anyone can log an expense. Types: Pantry, utilities (water, electricity), office supplies, rent, communication, load, transport, and one-offs. All are company expenses — no employee shoulders any expense.**

15. What is shown in the monthly summary?
    **A. `#`, `DATE`, `DAY`, `GROSS_INCOME`, `COMPENSATION_EXPENSE`, `OTHER_EXPENSE`, `TOTAL_EXPENSES`, `NET_INCOME`, `No_of_Clients`, `Employee` (ranked by seniority), `Coordinator (Manager)`. Includes totals row.**

### Remittance

16. Is remittance tracked per transaction or as a running balance?
    **A. Remittance is money sent to the bank after calculating net income. Manager handles it on their chosen day. Covers net income from the day after the previous remittance up to current day.**

17. Can the manager delegate remittance to someone else?
    **A. No. Only the Manager can remit.**

18. Can an employee see their own remittance history?
    **A. Remittance history shown to all. Admin and Accountant: all branches. Manager and Employee: own branch only.**

### Employees & Access

19. Who can register new employee accounts?
    **A. Admins and Managers.**

20. Can an employee belong to multiple branches?
    **A. Belongs to one branch. May temporarily relieve another branch.**

21. When an employee is deactivated, what happens to their records?
    **A. Resigned employees appear as-is on past records.**

### Daily Sales

22. Can all employees see everyone's sales for the day?
    **A. Employees can see their own branch's daily sales only.**

23. Can past days be viewed?
    **A. All roles can view the whole history.**

24. Is there a need to print or export?
    **A. Export daily sales and summary reports (monthly, all-time).**

### General

25. Are there any roles not yet covered?
    **A. Accountant (read-only, all branches). Temporary/relief employee (read-only for the day they worked at another branch, name pending).**

26. Is there anything the app absolutely must do that hasn't been covered?
    **A. Nothing else comes to mind yet — maybe during product demo.**

---

## Questions for Dad — Round 2 (Answered)

### Temporary / Relief Employees

27. When a relief employee is assigned to another branch temporarily, how is that recorded?
    **A. Employee taps a button when they first open the app to indicate they are present at a selected branch. The manager of a branch can also mark employees present. Data can be viewed outside working hours so there is no time-bounded access cutoff.**

28. Can a relief employee still see their home branch data while on relief duty at another branch?
    **A. Yes. Additionally, there are two special event types: provincial tour (admin-only report) and medical mission (visible to all roles).**

### Compensation & "Working That Day"

29. How is "working that day" defined for product commission splits?
    **A. Anyone who was at work (present/attended) that day counts.**

30. Can an employee have zero compensation for a day?
    **A. Never zero — even if no clients that day, some amount is always provided.**

### Remittance Scope

31. Is a remittance broken down per day or recorded as a lump sum?
    **A. Shows the total remitted covering the date range, plus the separate daily totals for each day in the range.**

32. Can there be multiple remittances in a single day?
    **A. At most one per day.**

### Inventory

33. When stock is marked as missing, is a reason recorded?
    **A. Reason can be added as an optional entry.**

34. Who can deduct tester samples from stock?
    **A. Any employee.**

35. Can a product belong to more than one category?
    **A. Exactly one category.**

### Sessions

36. Is the session number automatic or manually entered?
    **A. Automatic and global across all branches. First visit anywhere = Regular (1st). Second = 2nd Session. Third and beyond = Subsequent.**

37. Can a single session have multiple practitioners?
    **A. Yes. Remarks can be added.**

### Clients

38. Is the "clinic" field always the branch where the session is logged?
    **A. Clients can visit any branch. Session type is determined by their global history across all branches.**

---

## Questions for Dad — Round 3 (Pending)

### Provincial Tour & Medical Mission

39. Does a provincial tour belong to a specific branch, or is it a standalone event not tied to any branch?
    - This affects where its financial data lives — does the income and compensation roll into a branch's monthly summary, or does it appear only in the tour's own report?

40. Do provincial tour and medical mission sessions count toward a client's global session number history?
    - e.g. If a client's first-ever visit is at a provincial tour, are they "2nd Session" the next time they visit a regular branch?

41. Do employees receive daily compensation for provincial tour days using the same Admin/Manager assignment flow as regular branch days?

42. For medical missions, when Admin delegates attendance assignment to someone else, is that delegation only for that one event and expires after the day?

43. For medical missions, since sessions are free, what is recorded as gross income for that day — zero? Or is it tracked separately and excluded from financial summaries entirely?

### Remittance Across Branches

44. When a manager handles remittances for multiple branches, is that a permanent assignment set up by Admin, or is it ad hoc (any manager can remit for any branch at any time)?

45. Can two managers both be assigned to remit for the same branch, or is it always exactly one manager per branch?

### Pricing & Session Types

46. A session has both an auto-assigned type (Regular, 2nd Session, Subsequent) and a practitioner-adjustable price. Are these always two independent fields on the session record — i.e. the type never changes, only the price can be overridden?

47. Provincial tour pricing starts at ₱3500 for Regular and drops by ₱500 for each subsequent session. Does this use the client's global session count to determine which tier applies, or does the tour have its own separate pricing ladder regardless of the client's history elsewhere?

### Access Control

48. When a temporary employee checks in at another branch, can they still see their home branch data on the same day, or does the check-in at the other branch replace their home branch access for that day?

### Seniority

49. The monthly summary shows employees ranked by seniority. Where does seniority come from — is it a field manually set on the employee record, or is it derived from something like hire date?
