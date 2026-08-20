# Gate Ledger: Test Teardown Foreign-Key Graph

## Acceptance

- [x] Inventory movement graph matches `inventory_movement` references to product, product sale, branch, branch day, and app user.
- [x] Medical mission delegate graph matches references to app user and branch only.
- [x] Notification graph matches references to session, app user, and branch.
- [x] Schema-backed teardown regression inserts and cleans all three affected child rows with their tracked parents.

## Verification

- Focused test: `./gradlew :backend:test --tests com.companyb.companyapp.test.BasePostgresTestTeardownTest` PASS.
- `./gradlew :backend:ktlintCheck :backend:detekt` PASS.
- Test-data cleanliness check PASS after focused and full runs.
- Full `./gradlew :backend:test`: 970 tests, 185 failures in existing authorization/fixture paths; no teardown regression failure. Failures reproduce the known stale Branch Day capability fixture issue documented in `docs/agents/wayfinder-359-handoff.md`.

## Review

- Profile: Standard.
- P1 Spec: zero HARD findings.
- P2 Standards: zero HARD findings; test-only Exposed DSL and disposable test DB rules followed.
- P3 Behavior: zero affected-scope HARD findings.
- P4 Adversarial: zero affected-scope HARD findings. Existing unrelated FK graph omissions remain outside #303 scope.
- SOFT disposition: parent-row counts are not asserted individually; cleanup failure itself aborts the test, while child-row assertions prove target rows are gone.
