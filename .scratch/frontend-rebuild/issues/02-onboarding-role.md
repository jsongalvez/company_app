# 02 — Rename VIEWER to ONBOARDING

**What to build:** Rename the `VIEWER` role to `ONBOARDING` in the V2 seed migration and remove all `role_capability` rows for it. Update all code references (detekt config, test helpers, docs). ONBOARDING has zero capabilities — a freshly registered account sees nothing until assigned to a branch.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `V2__seed_roles_capabilities.sql`: VIEWER row renamed to ONBOARDING
- [ ] All `role_capability` INSERT rows for VIEWER/ONBOARDING removed (the role has zero capabilities)
- [ ] Any code references to VIEWER updated (detekt configs, `DatabaseTestHelper`, seed-data comments, PRD docs)
- [ ] `CONTEXT.md` and `docs/adr/0002-onboarding-role.md` already reflect ONBOARDING — no changes needed
- [ ] `./gradlew :backend:test` passes (no existing tests reference VIEWER by role name since tests use capabilities, but verify)
- [ ] `./gradlew :backend:detekt :backend:ktlintCheck` passes
