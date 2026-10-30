# ADR-0025: Commission is private to the branch; who else gets a member's rights

**Status:** Accepted
**Date:** 2026-10-30

## Context

The user (owed #26, message 13114): "Commission isn't part of owner's pocket ... It's like their tip, it should be private." And: "Only members of the same branch can see their commisions, since they would be sharing it irl."

Before V22, commission splits were readable by anyone with `VIEW_BRANCH_DATA`, which includes the Owner and the Accountant at every branch (leg (b), ADR-0023 amendment #431). Under the user's rule they must not see them.

Three other kinds of people work a branch without being its members. The user ruled on each:

- **Relief** (owed #23): "relief has same access as branch members only for that granted day."
- **A Branches-page helper** (`/api/delegates`, owed #28): "A helper shares commissions, just think of it as a relief but indefinite until removed."
- **The Owner and Accountant** see every branch, view only (owed #23). They see no commission. Answers to Opt's questions: "Accountants see remittance lists (view only, any branch)", and "A non-member Owner can pay members for earlier days too, not just today (wages and allowances only; commission stays private)."

## Decision

`VIEW_COMMISSION` is its own capability, separate from `VIEW_BRANCH_DATA`. Commission reads check it through `Access.Act.SEE_COMMISSION`: `/api/commission-splits` (`CommissionRoutes`) and the commission on a day's product sales (`ProductSaleRoutes`). Everything else on the day checks `VIEW_BRANCH_DATA`. The `active_user_capabilities` view grants it in four places:

| Who | Leg | Context | Until |
|---|---|---|---|
| A branch member (any role but Onboarding) | (d) membership | BRANCH | the assignment ends |
| Relief | (a2), from its `EDIT_BRANCH_DATA` grant | BRANCH_DAY | its granted day |
| A helper (`MEDICAL_MISSION_DELEGATE` source) | (a2), from its `EDIT_BRANCH_DATA` grant | BRANCH | the helper is removed (`valid_to` set) |
| Superuser (the dev account) | (e) | every BRANCH | always |

No role holds `VIEW_COMMISSION` GLOBAL: not the Owner, the Accountant, a Manager, or a Coordinator outside their branch.

Relief and helpers get the same set from (a2): `VIEW_BRANCH_DATA`, `VOID_SESSION` and `VIEW_COMMISSION`. So they have a member's rights, relief for one day and a helper for as long as the grant is open (V22 for relief, V23 for helpers).

Two related rules shipped with this decision:

- **Remittance lists:** `GET /api/remittances` passes for `SUBMIT_REMITTANCE` at the branch, or for `VIEW_BRANCH_DATA` everywhere (Accountant, Owner, Superuser). Only remitters write (`Access.Act.SUBMIT_REMITTANCE`).
- **Pay on earlier days:** pay and allowances on a past, unremitted day need the pay gate and a reason. They no longer need `EDIT_PAST_DAY` (`DayWrite.PAY` in `BranchDayService`). A remitted day still needs `EDIT_PAST_DAY`. The Owner pays every branch from `#/pay`, which shows wages and allowances and never commission.

## Consequences

- The table in `AccessTableTest` pins each rule cell by cell. It has a column per kind of person, including REL (relief), HLP (helper) and HLX (removed helper), so a change to any leg shows up as named wrong cells.
- Removing a helper (`closeDelegateCapabilityInTransaction`) ends every right the grant gave, commission included, because the view filters on `valid_to`.
- The web checks the same codes: `can("VIEW_COMMISSION", branchId, dayId)` decides whether Finance loads commission shares, and `canEverywhere` decides the all-branch Remittance and Pay pages.
- Shipped in `V22__view_and_relief_rights.sql` (VIEW_COMMISSION, relief) and `V23__helper_is_indefinite_relief.sql` (helpers).
