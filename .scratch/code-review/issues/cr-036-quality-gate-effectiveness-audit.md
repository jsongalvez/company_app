# CR-036: Quality gate effectiveness audit — no false confidence

**Source:** Quality assurance

## What to build

A documented audit of every quality gate step — pre-commit, pre-push, and the k6 load-test suite — verifying that each step genuinely catches the class of problems it claims to catch. Each gate is tested with an intentional violation to prove it blocks. Blind spots and ineffective checks are flagged. The output is a report; follow-up fix tickets are filed separately.

## Acceptance criteria

- [ ] Each pre-commit step is tested against a deliberate violation to confirm it blocks the commit
- [ ] Each pre-push step is tested against a deliberate violation to confirm it blocks the push
- [ ] Bypass paths are documented (e.g. `--no-verify`, missing hooks)
- [ ] Gaps where a class of problem has no gate at all are identified
- [ ] Ineffective checks (compile-only, reminder-only, port-only) are flagged with false-confidence risk
- [ ] k6 load-test suite: which scripts are wired into which gate (if any), which thresholds are tested, how results flow into decision-making

## Gates to audit

### Pre-commit (`.githooks/pre-commit`)

| Step | Claims to catch | What could be false |
|------|----------------|---------------------|
| `ktlintFormat` + `git add -u` | Formatting / import infractions | Runs on *all* files, not just staged — can sweep unrelated changes into a commit. Does it actually leave the tree dirty if a rule fails? |
| `:backend:detekt` | Static analysis violations | `maxIssues: 0` is strict but only if the active rule set catches project-level smells. What does it actually block? |
| `:backend:ktlintCheck` | Formatting infractions (read-only) | Should be redundant with ktlintFormat — but is the check after format always clean? |
| `:backend:test` | Regressions / broken logic | 35 test files for 143 source files. Does the suite exercise critical paths or pass trivially? |
| `:backend:jmhClasses` | Benchmark compilation | Only *compiles* benchmarks — does not run them. No perf regression check at commit time. |
| `:composeApp:compileKotlinDesktop` + `compileDebugKotlinAndroid` | composeApp compilation | Skips iOS. Only desktop + android checked. |
| Postgres connectivity check | DB reachable | Checks TCP + auth only. Schema/table existence not verified. Empty DB passes. |
| App boot test | App starts and binds port | Does not hit any endpoint. A 500-on-every-request app passes. No health check. |

### Pre-push (`.githooks/pre-push`)

| Step | Claims to catch | What could be false |
|------|----------------|---------------------|
| JMH benchmark run | Performance regressions | Runs benchmarks but does NOT enforce baseline comparison. The check is a **reminder** — prints a message, then unconditionally passes. `check-baselines.sh` is manual and never executed by the hook. |

### k6 load-test suite

| Script | Claims to catch | What could be false |
|--------|----------------|---------------------|
| `scripts/load-test/baseline.js` | p95 latency + error rate (3 endpoints) | Entirely manual. Not wired into any hook or CI. Thresholds are a guideline, not a gate. |
| `tests/k6/full-suite.js` | Full API path latency + error rate (14 endpoints) | Entirely manual. Same as above. |
| `tests/k6/concurrency-test.js` | 409 + idempotency + version mismatch edge cases | Manual. No thresholds defined. |
| `tests/k6/authz-test.js` | Auth edge cases (invalid/expired token, insufficient capability) | Manual. No thresholds defined. |
| `tests/k6/remittance-race-test.js` | Serializable isolation race on remittance submit | Manual. No thresholds defined. |

### Cross-cutting blind spots

| Gap | Risk |
|-----|------|
| No CI/CD (no GitHub Actions, no pipelines) | Quality is entirely client-side. If hooks aren't installed or are skipped (`--no-verify`), nothing runs. |
| No server-side enforcement | A developer can push without ever running tests, lint, or k6. |
| No `:shared` module compilation check | Shared module is compiled transitively but never explicitly verified. |
| No migration verification | Flyway migrations are not checked — a bad migration breaks the app at boot but not at compile time. |
| k6 not automated | Threshold violations are never enforced; they're documentation, not gates. |

## Blocked by

None — can start immediately.

## Status: ready-for-agent
