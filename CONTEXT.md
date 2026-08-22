# CompanyApp

Internal operations app for a multi-branch physical therapy practice. Practitioners log sessions, coordinators handle finance and remittance, owners manage users and branches.

## Language

**Branch**:
A physical clinic (`CLINIC`), a temporary off-site event (`PROVINCIAL_TOUR`), or a free medical mission event (`MEDICAL_MISSION`). Each holds its own inventory, sessions, and financial records.
_Avoid_: Location, site, clinic (ambiguous — some branches aren't clinics)

**Session**:
One visit by one client at one branch. Has a type (auto-assigned from client history), a status (PENDING → COMPLETED/NO_SHOW/CANCELLED), a final price (defaults to base rate, overridable), and assigned practitioners. Walk-in sessions cannot be marked as NO_SHOW or CANCELLED.
_Avoid_: Appointment, booking, visit

**Client**:
A global person record shared across all branches. Has at most one PENDING session at a time. Can be anonymized (soft-delete + PII nullification) while retaining gender and age for reporting.
_Avoid_: Patient, customer

**Gender**:
A domain enumeration (`M`, `F`) used in client records.
_Avoid_: sex, gender-string

**Practitioner**:
A user who works on clients during sessions. Logs sessions, manages inventory, views clients. Full access to home branches and any branch checked into that day.
_Avoid_: Therapist, doctor, staff

**Coordinator**:
A user who handles finance and remittance for assigned branches. Sole editor of PAST and REMITTED records. A MANAGER is a superset of Coordinator with additional user-management and delegate-assignment powers.
_Avoid_: Admin, finance officer

**Accountant**:
A read-only user who can view sales and data for all branches. No edit capabilities.
_Avoid_: Auditor, bookkeeper

**ONBOARDING**:
A freshly registered user with zero capabilities. Functionally locked out until `MANAGE_USERS` assigns them to a branch, at which point they gain practitioner access.
_Avoid_: New user, unassigned, pending

**Role**:
A predefined bundle of capabilities (seeded in V2). GLOBAL-scoped capabilities derive from the user's role through the capability view; BRANCH-scoped grants come from direct grants (relief, delegate).
_Avoid_: Position, title

**Deactivate**:
The act of setting a user INACTIVE: login is blocked immediately (active JWT killed), all capabilities vanish, records and branch assignments are kept. Reversible via Reactivate. The deactivation time is recorded (`deactivated_at`).
_Avoid_: Disable, ban, delete

**Reactivate**:
The act of restoring a deactivated user to ACTIVE. Capabilities return through the capability view; the user logs in fresh (the old JWT stays dead).
_Avoid_: Re-enable, unban

**Relief Duty**:
When any user clocks into a non-home branch. Starts with view-only access; edit access requires a relief grant (a user-initiated broadcast request approved by any branch member, or branch-initiated via a relief invite). Multiple relief workers may hold edit access at one branch on the same day. Expires at 04:00 Manila the next day. Compensation is paid from the relief branch's drawer.
_Avoid_: Temporary assignment, loaned staff

**Relief Request**:
The outsider-initiated ask for relief access at one branch for one day (today or a future date). Broadcast to the whole branch — it names no individual. One live request per requester per branch per date; any active branch member grants, denies, or cancels it; the requester withdraws their own. All retraction locks once the requester clocks in as relief.
_Avoid_: Access request (legacy targeted form), shift request

**Relief Invite**:
The branch-initiated offer of relief access for a single future day. Any user assigned to the branch can invite any active user; the invitee accepts or declines. Accepting writes the day's relief grant. Distinct from a relief request, which the relief user initiates.
_Avoid_: Shift offer, temporary assignment offer

**Branch Day**:
One operational business day at one branch, identified by its calendar date. The operational-day boundary is 04:00 Asia/Manila — a branch day stays editable until 04:00 the following morning, then transitions lazily (see Day State). `BranchDayService.currentOperationalDate` is the sole authority for deriving the current operational date; consumers delegate to it and never derive day semantics from the wall clock themselves.
_Avoid_: Business day, calendar day (ambiguous at the boundary), operating day

**Day State**:
Every branch day has a status. `OPEN` (current day, editable by all on-duty users) transitions lazily to `PAST` at 04:00 AM Manila the following day. `REMITTED` days are covered by a submitted remittance and require Coordinator-only edits with flagged audit entries.
_Avoid_: Day status, day phase

**Remittance**:
The act of submitting session income to the business. Two independent flows: SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity). At submission, an immutable financial snapshot freezes the P&L state. Drafts are unconstrained and can overlap.
_Avoid_: Payout, cash-out, settlement

**Snapshot**:
An immutable financial record written at remittance submission time. Cannot be updated or deleted — except by an Undo within 48 hours of submission. Later edits to the underlying session/expense/compensation data do not retroactively change the snapshot.
_Avoid_: Freeze, archive

**Undo**:
The act of reverting a submitted remittance within 48 hours of submission: the remittance returns to Draft, the covered days unlock, and the frozen snapshot is deleted. Requires a reason, recorded in the audit trail. Time-limited — after the window closes, the snapshot is permanent.
_Avoid_: Reverse, cancel, refund

**Void**:
The act of excluding a session from financial calculations while preserving its record. Requires a reason. Can be undone (unvoided) if done in error. A voided session remains visible with a clear indicator.
_Avoid_: Delete, remove, cancel

**Commission Split**:
Product commissions are pooled per branch day and split equally among all practitioners and coordinators clocked in at the `sold_at` time. Manual inclusions/exclusions can override. Separate from compensation and not subject to remittance.
_Avoid_: Bonus, tip (the term is "commission" per the business)

**Capability**:
A fine-grained permission code (e.g. `EDIT_BRANCH_DATA`, `SUBMIT_REMITTANCE`) scoped to a context (`BRANCH`, `BRANCH_DAY`, `GLOBAL`). Roles are bundles of capabilities; runtime checks use capabilities, not role names.
_Avoid_: Permission, right, role-check

**Branch Slot**:
A cosmetic ordering number (1 = senior) per user per branch assignment. Controls display order in reports and sessions. Relief practitioners sort after home slots.
_Avoid_: Rank, seniority number

**Notification**:
An in-app alert about an upcoming appointment, delivered to the coordinator(s) assigned to the session's branch. Contains a human-readable `message`, the originating `session_id`, and a read/unread status.
_Avoid_: Alert, reminder, push notification

**Audit Log**:
An immutable record of every mutation in the system — who changed what, when, and (optionally) why. Each entry captures the table name, record id, action (INSERT/UPDATE/DELETE), caller, and before-and-after field snapshots.
_Avoid_: Change log, history, event log
