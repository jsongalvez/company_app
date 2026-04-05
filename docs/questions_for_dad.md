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
- Roles: `ADMIN`, `MANAGER`, `EMPLOYEE`, `VIEWER`
    - `ADMIN` — owner, full access, manages employees
    - `MANAGER` — handles finance and remittance, can delegate remittance
    - `EMPLOYEE` — practitioners, log sessions, view clients, manage inventory
    - `VIEWER` — read-only, future use

### Clients
- Clients book sessions or walk in — treated identically once the session starts
- Each client has a running log of all their sessions
- Client fields: name, cellphone number

### Sessions
- A session records one visit by a client
- Session fields: date, client name, cellphone, concern/illness, session number (1st, 2nd, etc.), next appointment date, practitioner, payment for service, products bought (with price per item)
- Next appointment is approximate — not all clients follow through

### Products & Inventory
- Employees can add incoming stock
- Clients can buy products during a session
- Product fields: name, price, amount in stock
- Stock is per-branch

### Finance
- Tracks: employee compensation, expenses (pantry, lights, etc.), gross income
- Gross income = money from clients − (compensation + expenses)
- Employees can do "remittance" — withdraw the amount owed to them
- The person handling money can delegate remittance approval to another user when away

### Daily Sales
- A list of all sessions for the day — visible to employees
- Includes a monthly summary (details TBD)

### Client Search
- Typeahead search as user types
- Fuzzy matching to handle typos (e.g. "Jhn" finds "John")
- Implementation: PostgreSQL `pg_trgm` + `ILIKE`, debounced ~300ms on frontend

---

## Questions for Dad

### Clients

1. Is a client shared across branches, or does each branch have their own separate client list?
    - e.g. If a client visits Branch A then Branch B, does Branch B see their history from Branch A?
A. Yes, a client can appear in multiple branches.

2. Does a client have a fixed assigned practitioner, or can any practitioner work on them?
A. Can request a specific practitioner, but if the practitioner is not available any other practitioner will be provisioned

3. Can a client have multiple concerns/illnesses logged per session, or just one?
A. Their current workflow is that a waiver has checkboxes of common concerns and an "other" for those not covered

---

### Sessions / Appointments

4. When a next appointment is set, who gets notified when it's coming up?
    - The receptionist? The practitioner? Everyone?
    - How far in advance? (e.g. 1 day before, morning of?)
A. The Manager. 2 days before.

5. How should the app handle clients who don't follow through on their next appointment — is there anything that needs to be tracked or flagged, or just ignored?
A. Clients who don't follow through on their next appointment will be marked no-show. No additional actions are done.

6. Can a session be edited after it's been saved, or is it locked once submitted?
    - If editable — who can edit it? Anyone, or only the one who created it?
A. Editable. Employee, Manager, and Admin.

7. Is there a concept of a "cancelled" or "no-show" session that needs to be recorded?
A. Booked sessions are marked no-show or cancelled.

---

### Products & Inventory

8. When a client buys a product during a session, does stock automatically go down, or does someone manually update it?
A. Yes, it automatically goes down.

9. Can a product's price change over time? If so, does the session record the price at the time of purchase, or the current price?
    - Past records should show what was actually charged — this affects financial accuracy
A. A products price can change over time. The recorded price will be at the time of purchase. 

10. Who can add or edit products and update stock levels?
    - Any employee, or only managers/admins?
A. managers/admins

11. Is there a low-stock alert, or is that a future concern?
A. Yes. Manager is notified.

Additional note: The products have a flat value added as commission. (e.g. item is 400, commission is 100, customer pays 500). The commission is spread evenly to employees + managers working on that day.
---

### Finance & Compensation

12. How is a practitioner's compensation calculated?
    - Fixed salary per day/week/month?
    - Per-session commission?
    - A combination of both?
A. Compensation is per day. The current business flow is, Admin will view branch sales at end of day (client sessions that day, who is on-duty) then assign a value that will be the compensation of that employee.
   Then either the Admin or Manager will be assigning the pay for the employee for that day. The remaining income after employee compensation and expenses are deducted from the gross income (from all sessions) are then remitted to the bank by the Manager. 
   Product sales are their own thing, commissions are treated as "tips" and are bonuses for employees. 

13. Is the compensation the same for all practitioners, or does it vary per person?
A. Vary. Admin supplies the amount paid based on what he sees on the daily sales from client sessions.

14. What counts as a business expense?
    - Just recurring things like pantry and utilities, or can one-off expenses be logged too?
    - Who can log an expense?
A.  Anyone can log an expense. Pantry, utilities (water, electricity), office supplies, rent, communication, load, transport, and one-offs
Additional note: expenses are treated as company expenses. No employee will shoulder expenses, it is deducted from gross daily income.
15. What exactly is shown in the monthly summary?
    - Total income? Per-practitioner breakdown? Expense breakdown? Net profit?
A. #	DATE	DAY	 GROSS_INCOME  	 COMPENSATION_EXPENSE 	 OTHER_EXPENSE 	 TOTAL_EXPENSES  	 NET_INCOME 	 No_of_Clients 	 Employee (ranked by seniority) Coordinator(Manager)  
   include total of GROSS_INCOME  	 COMPENSATION_EXPENSE 	 OTHER_EXPENSE 	 TOTAL_EXPENSES  	 NET_INCOME 	 No_of_Clients

---

### Remittance

16. Is remittance tracked per transaction (e.g. "Employee X withdrew ₱500 on April 3"), or just a running balance?
A. I misunderstood. Remittance is the money sent to the bank after calculating net income (after deductions of expense and compensation). Manager will handle remittance on the day of their choosing. The remitted values
   are the net income for the current day up to the day after the previous remittance.

17. When the manager delegates remittance handling to someone else, is that a permanent role change or a temporary one?
    - e.g. "While I'm away this week, User Y can approve remittances"
A. The Manager cannot delegate remittance. Only they are allowed to remit.

18. Can an employee see their own remittance history, or only the manager/admin can?
A. Remittance history is shown to all users. Admin for all branches, manager and employee for their own branch only. Accountant for all branches.

---

### Employees & Access

19. Who can register new employee accounts — admin only, or can a manager do it too?
A. Admins and managers.

20. Can an employee belong to multiple branches, or always just one?
A.  An employee belongs to one branch. Often times they are called upon to relieve other branches for a short period of time. 

21. When an employee is deactivated (e.g. they resign), what happens to their historical records?
    - Should their name still appear on past sessions they handled?
A. Resigned employees will appear as-is on past records.

Additional note: Admin can view stats of all branches. Manager/Employee/Viewer can only view stats for their own branch.
---

### Daily Sales

22. In the daily sales list, can all employees see everyone's sales for the day, or only their own?
A.  Employees can see their own branch's daily sales. They cannot see other branch's data.

23. Can past days be viewed, or only today?
A.  All roles can vew whole history.

24. Is there a need to print or export the daily sales list?
A.  Export daily sales, and summary reports (monthly, all-time)

---

### General

25. Are there any roles or job titles in the business that we haven't covered yet?
    - e.g. receptionist, cashier, supervisor?
A. Accountant. They are read-only. Can view sales of all branches.
   Temporary (name pending). Relief employee who temporarily takes up work for another branch. Can only see daily record for the day they worked at that branch.

26. Is there anything the app absolutely must do that we haven't talked about yet?
A. Can't think of one. Maybe during product demo.

Additional: 
- Clients also write their address, gender, age, clinic(branch they are visiting), blood pressure, other medical conditions (free text)
- address is a general location in the Philippines e.g.
  Las Pinas
  Bacoor, Cavite
  Valenzuela City
  Sucat, Paranaque
- Managers can edit the price per session type:
  Regular	2500
  2nd Session	2000
  Subsequent	1500
  Others	3500
  Others	2000
- expense types are:
  Pantry Items
  Communication/Load
  Water
  Transportation
  Electricity
  Rental
  Office Supplies
  Furnitures/Fixtures/Improvements
  Miscellaneous / Others
- products sales are like this:
  INCOME FROM ESSENTIAL OIL				
  Particulars	Rate	QTY	Amount
  Big Roll On	500	1	500
  Big Sprayer	500	1	500
  Small	300	0	0
  TOTAL	1000

INCOME FROM BIOMEKANIKS INFUSER				
Particulars	Rate	QTY	Amount
Machine	35000	0	0
Potassium	500	0	0
TOTAL	0

INCOME FROM MAGNESIUM SPRAY				
Particulars	Rate	QTY	Amount
MagSpray	500		0

			TOTAL	0

INCOME FROM K-ION				
Particulars	Rate	QTY	Amount
K-ION	500		0
- products can be used as tester samples and are deducted from stock.
- currently the manager takes inventory of stock like this:
  INCOME FROM ESSENTIAL OIL					
  Particulars	Unit Price	AVAILABLE	STOCK	SALES
  Big Roll On	500	5	50	43
  Big Sprayer	500	0	12	12
  Big Sprayer	500	2	30	27
  Small	300	 	22	19
  TESTER	B. Roll-on	2		
  B. Spray Old	1		
  B. Spray New	1		
  S. Spray New	2		  
  INCOME FROM BIOMEKANIKS INFUSER					
  Particulars	Rate		STOCK	SALES
  Machine	35000	2	2	0
  Potassium	400	14	50	35

  		H2 sample	1	
  		K+Ion Sample	1	
  		missing	1
- there is an option to mark inventory as missing X amount
- 
