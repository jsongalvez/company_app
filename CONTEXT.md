# CompanyApp

Internal operations app for a multi-branch physical therapy practice. Practitioners log sessions, coordinators handle finance and remittance, owners manage users and branches.

## Language

**Branch**:
A physical clinic (`CLINIC`), a temporary off-site event (`PROVINCIAL_TOUR`), or a free medical mission event (`MEDICAL_MISSION`). Each holds its own inventory, sessions, and financial records.
_Avoid_: Location, site, clinic (ambiguous — some branches aren't clinics)

**Session**:
One visit by one client at one branch. Has a type (auto-assigned from client history), a status (PENDING → COMPLETED/NO_SHOW/CANCELLED), a final price (defaults to base rate, overridable), and assigned practitioners.
_Avoid_: Appointment, booking, visit

**Client**:
A global person record shared across all branches. Has at most one PENDING session at a time. Can be anonymized (soft-delete + PII nullification) while retaining gender and age for reporting.
_Avoid_: Patient, customer

**Gender**:
A domain enumeration (`M`, `F`) used throughout the client lifecycle — from request DTOs through the service layer to DB persistence. Replaces raw string validation in route handlers.
_Avoid_: sex, gender-string

**Practitioner**:
A user who works on clients during sessions. Logs sessions, manages inventory, views clients. Full access to home branches and any branch checked into that day.
_Avoid_: Therapist, doctor, staff

**Coordinator**:
A user who handles finance and remittance for assigned branches. Sole editor of PAST and REMITTED records. A MANAGER is a superset of Coordinator with additional user-management and delegate-assignment powers.
_Avoid_: Admin, finance officer

**ONBOARDING**:
A freshly registered user with zero capabilities. Functionally locked out until `MANAGE_USERS` assigns them to a branch, at which point they gain practitioner access. Replaces the former `VIEWER` role.
_Avoid_: New user, unassigned, pending

**Relief Duty**:
When any user clocks into a non-home branch. Starts with view-only access; edit access requires a relief grant from a currently checked-in user at that branch. Expires at 04:00 Manila the next day. Compensation is paid from the relief branch's drawer.
_Avoid_: Temporary assignment, loaned staff

**Day State**:
Every branch day has a status. `OPEN` (current day, editable by all on-duty users) transitions lazily to `PAST` at 04:00 AM Manila the following day. `REMITTED` days are covered by a submitted remittance and require Coordinator-only edits with flagged audit entries.
_Avoid_: Day status, day phase

**Remittance**:
The act of submitting session income to the business. Two independent flows: SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity). At submission, an immutable financial snapshot freezes the P&L state. Drafts are unconstrained and can overlap.
_Avoid_: Payout, cash-out, settlement

**Snapshot**:
An immutable financial record written at remittance submission time. Cannot be updated or deleted (enforced by a DB trigger). Later edits to the underlying session/expense/compensation data do not retroactively change the snapshot.
_Avoid_: Freeze, archive

**Commission Split**:
Product commissions are pooled per branch day and split equally among all practitioners and coordinators clocked in at the `sold_at` time. Manual inclusions/exclusions can override. Separate from compensation and not subject to remittance.
_Avoid_: Bonus, tip (the term is "commission" per the business)

**Capability**:
A fine-grained permission code (e.g. `EDIT_BRANCH_DATA`, `SUBMIT_REMITTANCE`) scoped to a context (`BRANCH`, `BRANCH_DAY`, `GLOBAL`). All authorization checks go through the `active_user_capabilities` view — roles are never queried at runtime. Roles only seed initial capabilities.
_Avoid_: Permission, right, role-check

**Branch Slot**:
A cosmetic ordering number (1 = senior) per user per branch assignment. Controls display order in reports and sessions. Swappable atomically. Relief practitioners sort after home slots.
_Avoid_: Rank, seniority number

**Notification**:
An in-app alert about an upcoming appointment, delivered to the coordinator(s) assigned to the session's branch. Contains a human-readable `message`, the originating `session_id`, and a read/unread status. Written once daily at 07:00 AM Manila by the scheduler, never updated (only marked read).
_Avoid_: Alert, reminder, push notification

**Audit Log**:
An immutable record of every mutation in the system — who changed what, when, and (optionally) why. Each entry captures the table name, record id, action (INSERT/UPDATE/DELETE), caller, and before-and-after field snapshots. Written atomically with the mutation inside the same database transaction.
_Avoid_: Change log, history, event log
