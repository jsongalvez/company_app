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
    - `ADMIN` — owner, full access, manages employees, views stats of all branches
    - `MANAGER` — handles finance and remittance, registers employees, views own branch only
    - `EMPLOYEE` — practitioners, log sessions, view clients, manage inventory, views own branch only
    - `VIEWER` — read-only, future use
    - `ACCOUNTANT` — read-only, views sales of all branches
    - `TEMPORARY` (name pending) — relief employee, can only see daily records for the day they worked at that branch

### Clients
- Clients book sessions or walk in — treated identically once the session starts
- A client can appear in multiple branches (shared across branches)
- Each client has a running log of all their sessions
- Client fields: name, cellphone number, address (general location e.g. "Las Pinas", "Bacoor, Cavite"), gender, age, branch they are visiting, blood pressure, other medical conditions (free text)
- A client can request a specific practitioner; if unavailable, any available practitioner is assigned

### Sessions
- A session records one visit by a client
- Session fields: date, client name, cellphone, concerns/illnesses (checkboxes from a waiver with common concerns + "other" free text), session number (1st, 2nd, etc.), next appointment date, practitioner, payment for service, products bought (with price per item at time of purchase)
- Session types and rates (editable by Manager):
    - Regular — ₱2500
    - 2nd Session — ₱2000
    - Subsequent — ₱1500
    - Others — ₱3500
    - Others — ₱2000
- Sessions can be edited after submission by Employee, Manager, or Admin
- Booked sessions can be marked as **no-show** or **cancelled**
- Walk-in sessions cannot be no-show/cancelled (they already happened)
- Next appointment is approximate — clients who don't follow through are marked **no-show** with no additional action required

### Products & Inventory
- Products are grouped by category (e.g. Essential Oil, Biomekaniks Infuser, Magnesium Spray, K-ION)
- Product fields: name, unit price, commission amount (flat value added on top; customer pays price + commission)
- Commission from product sales is spread evenly among all employees and managers working that day
- Product commissions are treated as "tips"/bonuses — separate from regular compensation
- Stock is per-branch
- When a client buys a product during a session, stock automatically decreases
- The recorded price is the price at the time of purchase (not the current price)
- Products can be used as tester samples — deducted from stock manually
- Inventory can be marked as missing (mark X amount as missing)
- Only managers and admins can add or edit products and update stock levels
- Inventory tracking includes: available stock, total stock, sales quantity
- Low-stock alerts notify the Manager

### Finance & Compensation
- Compensation is per day, assigned manually by Admin after viewing daily sales
- Admin views: sessions for the day + who is on duty, then assigns a compensation value per employee
- Either Admin or Manager assigns the pay for each employee for the day
- Compensation varies per employee — Admin decides the amount based on daily sales
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
- Product sales (commissions) are separate from session income — treated as bonus/tips for employees

### Remittance
- Remittance = the net income sent to the bank after deducting all expenses and compensation
- Manager handles remittance on the day of their choosing
- A remittance covers all net income from the day after the previous remittance up to the current day
- Only the Manager can perform remittance — cannot be delegated
- Remittance history is visible to all users:
    - Admin: all branches
    - Manager/Employee: own branch only
    - Accountant: all branches

### Daily Sales
- A list of all sessions for the day — visible to employees of the same branch only
- Employees cannot see other branches' data
- All roles can view the full history (not just today)
- Monthly summary columns:
    - `#`, `DATE`, `DAY`, `GROSS_INCOME`, `COMPENSATION_EXPENSE`, `OTHER_EXPENSE`, `TOTAL_EXPENSES`, `NET_INCOME`, `No_of_Clients`, `Employee` (ranked by seniority), `Coordinator (Manager)`
    - Totals row at the bottom for all numeric columns

### Client Search
- Typeahead search as user types
- Fuzzy matching to handle typos (e.g. "Jhn" finds "John")
- Implementation: PostgreSQL `pg_trgm` + `ILIKE`, debounced ~300ms on frontend

### Access Control Summary
- Admin can view stats of all branches
- Manager, Employee, Viewer can only view stats for their own branch
- Accountant can view sales of all branches (read-only)
- Temporary employee can only see daily records for the day they worked at a given branch
- Resigned employees appear as-is on historical records

### Exports
- Export daily sales
- Export summary reports (monthly, all-time)

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

## Questions for Dad — Round 2 (Pending)

### Temporary / Relief Employees

27. When a relief employee is assigned to another branch temporarily, how is that recorded?
    - Is there an explicit "assignment" that has a start and end date?
    - Or is it implied by which branch they log sessions at that day?
    - This matters because their access to that branch's daily data is time-bounded — the system needs to know *when* they were there.

28. Can a relief employee still see their home branch data while on relief duty at another branch?
    - Or is their access completely switched to the relief branch for that period?

### Compensation & "Working That Day"

29. When product commissions are split evenly among employees and managers "working that day," how is "working that day" defined?
    - Is it anyone who has at least one session logged that day?
    - Or is there an explicit check-in / shift record?
    - This is important because the commission split needs a definitive list of who qualifies.

30. When Admin assigns compensation at end of day, is it possible for an employee to have zero compensation for a day (e.g. they were present but had no sessions)?
    - Or does Admin only assign compensation to employees who actually handled sessions?

### Remittance Scope

31. Remittance covers net income from multiple days. When a remittance is recorded, does it need to be broken down per day, or is it recorded as a single lump sum for the covered period?
    - e.g. Does the system store "₱X remitted, covering April 1–April 5" or does it store five separate daily totals?

32. Can there be multiple remittances in a single day, or at most one per day?

### Inventory

33. When stock is marked as "missing," is a reason recorded, or just the quantity?

34. Tester samples are deducted from stock manually — who is allowed to do this? Any employee, or managers/admins only?

35. Can a product belong to more than one category, or is it always in exactly one?

### Sessions

36. The session has a "session number" (1st, 2nd, etc.) — is this automatically calculated based on the client's history at that branch, or does the practitioner enter it manually?

37. Can a single session have multiple practitioners (e.g. two practitioners working on one client at the same time)?

### Clients

38. The client form includes "clinic (branch they are visiting)" — is this always the branch where the session is being logged, or can a client be registered at one branch and visit another?
