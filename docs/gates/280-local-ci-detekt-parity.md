# Gate Ledger: Local and CI Detekt Parity

## Scope

Child #280 makes CI use the same Gradle Detekt task graph and compiler-warning
policy already enforced by pre-commit. Rule configuration remains owned by the
root Gradle Detekt setup; no duplicate policy was added.

## Evidence

- [x] G1: Local and CI task graph matches
  CHECK: compare `.githooks/pre-commit:62-76` with `.github/workflows/quality.yml:88-105`.
  EXPECT: Same backend, shared, Compose, test, compile, and `-x :backend:publishOpenApiSpec` tasks.
  EVIDENCE: Source comparison passed; Gradle dry-run completed successfully for the complete task graph.
- [x] G2: Compiler warning policy matches
  CHECK: `grep -F -- '-PwarningsAsErrors=true' .githooks/pre-commit .github/workflows/quality.yml`.
  EXPECT: Both mandatory paths pass the same property.
  EVIDENCE: Both paths contain `-PwarningsAsErrors=true`; `:shared:compileKotlinJvm -PwarningsAsErrors=true` passed.
- [x] G3: Fail-closed negative control
  CHECK: `./gradlew :backend:detekt -PwarningsAsErrors=true --no-daemon`.
  EXPECT: Non-zero while existing findings remain; no baseline or broad exclusion converts failure to success.
  EVIDENCE: Failed with 293 weighted findings, owned by closeout child #281.
- [x] G4: Gate hygiene
  CHECK: `bash -n .githooks/pre-commit && git diff --check`.
  EXPECT: EXIT 0.
  EVIDENCE: PASS. YAML block indentation and command continuation were source-checked; no YAML parser is installed locally.

## Disposition

CI now fails on the same compiler warnings as local pre-commit and consumes the
same task list. Existing Detekt findings remain fail-closed for #281; this child
does not weaken policy, add a baseline, or claim the rollout is green.
