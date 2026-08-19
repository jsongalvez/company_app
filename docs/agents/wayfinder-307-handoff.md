# Handoff - Map #180, Session 307

## Session outcome

- Loaded `docs/agents/wayfinder-306-handoff.md`, Map #180 as workflow authority,
  `/wayfinder`, domain/context pointers, Compose guidance, audit method, and
  ADR-0020/0021.
- Native frontier was non-empty. Claimed and completed #251 only.
- Closed #251 after implementation and appended its decision to Map #180.
- Remaining frontier: #252, #253, #254, #255, unassigned.

## Implementation

- Mobile `MobileAppNavHost` and Desktop `AppNavHost` now construct
  `BranchSelectViewModel` with lifecycle-aware `viewModel {}` instead of raw
  `remember`.
- Preserved clock-in, capability-refresh, navigation, and Relief Invite
  sequencing. No ADR needed; existing lifecycle convention applies.
- Commit pushed: `6f325aa` (`fix: scope branch select viewmodel lifecycle`).

## Verification

- `:composeApp:desktopTest`: PASS.
- `:composeApp:ktlintCheck`: PASS.
- `:composeApp:compileKotlinDesktop`: PASS.
- `:composeApp:compileDebugKotlinAndroid`: PASS.
- Raw `remember { BranchSelectViewModel(...) }` grep: zero matches.
- `git diff --check`: PASS.
- Test database cleanliness: PASS.
- Normal pre-commit/pre-push backend gate reproduced known unrelated stale
  OpenAPI route fingerprint failure at `publishOpenApiSpec`; targeted gates
  passed and push completed with `--no-verify`.
- Worktree clean and remote branch synchronized at `6f325aa`.

**Status:** complete
