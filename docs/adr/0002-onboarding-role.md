# ONBOARDING role (formerly VIEWER)

The `VIEWER` role was defined as "read-only, future use" with only `VIEW_BRANCH_DATA`. It had no users and no clear purpose.

**Decision:** Rename `VIEWER` to `ONBOARDING`. Give it zero capabilities — a freshly registered account sees nothing. When `MANAGE_USERS` assigns the user to a branch, they functionally become a practitioner with all the capabilities that entails.

**Trade-off:** This changes the role's semantics from "read-only observer" to "zero-permission new account." If a read-only observer role is ever needed, it can be added separately. The migration replaces `VIEWER` with `ONBOARDING` in the seed data; the V2 migration row changes, but since no users held this role, there is no data migration cost.

**Rejected:** Keeping VIEWER as-is. It served no purpose and would confuse the UI (does a VIEWER see the dashboard? the drawer?).
