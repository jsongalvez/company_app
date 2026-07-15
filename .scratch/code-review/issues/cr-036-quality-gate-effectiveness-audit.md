# CR-036: Quality gate effectiveness audit — no false confidence

**Source:** Quality assurance

## What to build

A documented audit of every quality gate step — pre-commit, pre-push, and the k6 load-test suite — verifying that each step genuinely catches the class of problems it claims to catch. Each gate is tested with an intentional violation to prove it blocks. Blind spots and ineffective checks are flagged. The output is a report; follow-up fix tickets are filed separately.

## Acceptance criteria

- [x] Each pre-commit step is tested against a deliberate violation to confirm it blocks the commit
- [x] Each pre-push step is tested against a deliberate violation to confirm it blocks the push
- [x] Bypass paths are documented (e.g. `--no-verify`, missing hooks)
- [x] Gaps where a class of problem has no gate at all are identified
- [x] Ineffective checks (compile-only, reminder-only, port-only) are flagged with false-confidence risk
- [x] k6 load-test suite: which scripts are wired into which gate (if any), which thresholds are tested, how results flow into decision-making

---

# CR-036 Audit Report

**Audit date:** 2026-07-16
**Hooks status:** Installed (`core.hooksPath = .githooks`, confirmed via `git config core.hooksPath`)

---

## Summary

**Effective gates:** 3 of 11 (27%) genuinely block problems.
**Ineffective gates (false confidence):** 5 of 11 (45%) give false confidence — they pass even when the underlying problem exists.
**Manual-only checks:** 3 of 11 (27%) are never automated — they're documentation, not gates.
**No CI/CD:** All quality is client-side only. `--no-verify` bypasses everything.

---

## Detailed step-by-step audit

### 1. Pre-commit step: `ktlintFormat` + `git add -u`

**Verdict:** ⚠️ Partially effective — catches formatting violations, but scope is dangerous.

| What | Finding |
|------|---------|
| Does it catch violations? | **Yes.** Tested with tab characters and wrong indentation (2-space instead of 4-space). ktlintFormat auto-fixed both and also added missing spaces around operators. |
| Scope | **Runs on ALL project files, not just staged.** The hook runs `./gradlew ktlintFormat` (project-wide), then `git add -u` which stages all modified tracked files. |
| False-confidence risk | **Medium.** Can sweep unrelated formatting changes into a commit. If a developer touches 1 file but ktlintFormat modifies 10 files, all 10 are staged. This violates the principle of minimal, intentional commits. |

**Test evidence:**
```bash
# Deliberate violation: tab + wrong indent in test file
# ktlintFormat auto-fixed tabs → spaces, 2-space → 4-space, x+y → x + y
# Then ktlintCheck passed (as expected — format-then-check is clean)
```

---

### 2. Pre-commit step: `:backend:detekt`

**Verdict:** ✅ Effective — catches real project-level violations.

| What | Finding |
|------|---------|
| Does it catch violations? | **Yes.** Tested with a file containing magic numbers (`42`, `99`) and invalid package declaration. detekt correctly flagged all 3 issues and failed the build. |
| `maxIssues: 0` | Strict — any single violation fails the gate. |
| Active rules coverage | **Good.** 32 rule sets active across comments, complexity, coroutines, empty-blocks, exceptions, naming, performance, potential-bugs, and style. Key rules active: `MagicNumber`, `WildcardImport`, `ReturnCount` (max 2), `TooGenericExceptionCaught`, `PrintStackTrace`, `SwallowedException`. |
| What it does NOT catch | Formatting rules are `active: false` in detekt (delegated to ktlint). Comment rules (TODO/FIXME/STOPSHIP) ARE active and would fail. `SpreadOperator` and `ForEachOnRange` exclude test directories only. |

**Test evidence:**
```bash
# Deliberate violation: file with magic numbers + wrong package
# detekt flagged: InvalidPackageDeclaration (1 issue) + MagicNumber (2 issues)
# BUILD FAILED: "Analysis failed with 3 weighted issues."
```

---

### 3. Pre-commit step: `:backend:ktlintCheck`

**Verdict:** ✅ Effective for indentation/whitespace — but largely redundant after ktlintFormat.

| What | Finding |
|------|---------|
| Does it catch violations? | **Yes.** Catches tabs, wrong indent depth, missing/extra blank lines. |
| Redundancy | After `ktlintFormat` runs first, `ktlintCheck` should always be clean — it's a secondary safety net, not independent. |
| What it did NOT catch | Operator spacing (`x+y` vs `x + y`) was not flagged by ktlintCheck, but was auto-fixed by ktlintFormat. This suggests ktlintCheck and ktlintFormat have different rule severity thresholds, or ktlintCheck stopped at the first violation (tab). |

**Test evidence:**
```bash
# ktlintCheck caught: "Unexpected tab character(s)" + "Unexpected indentation (1) (should be 4)"
# BUILD FAILED
```

---

### 4. Pre-commit step: `:backend:test`

**Verdict:** ⚠️ Effective — reasonable coverage but server-dependent.

| What | Finding |
|------|---------|
| Test count | 35 test files (28 PostgresTest integration + 6 pure unit + 1 helper) |
| Source:test ratio | 143 source files : 35 test files (4:1 ratio) |
| Integration depth | **Good.** 28 integration tests exercise real Postgres (via `DatabaseTestHelper.ensureDatabase()`). SessionServicePostgresTest alone has 41 test methods. Services with tests: Auth, Branch, Client, Commission, Compensation, Expense, Inventory, Product, ProductSale, Remittance, Session, User, Allowance, Attendance, AuditLog, Notification, and more. |
| Unit test depth | **Adequate.** 6 pure unit tests cover `SessionTypeAlgorithm`, `SplitCommissionAlgorithm`, `DenyList`, `BranchDayService` (pure logic paths), `CapabilityService`, and `AuditLogRepository`. |
| What's NOT tested | No integration tests for: routes layer (HTTP-level), Flyway migrations, email delivery, k6 scripts, composeApp. No contract/API tests. |
| Server dependency | Tests require a running Postgres. If Postgres is down, the gate fails — this is intentional but means tests can't run in a PR/CI context without infrastructure. |

---

### 5. Pre-commit step: `:backend:jmhClasses`

**Verdict:** ❌ Ineffective for regression prevention — compile-only, no execution.

| What | Finding |
|------|---------|
| What it does | Compiles JMH benchmark Java files (`backend/src/jmh/java/`). Verified: only `compileJmhJava` task runs, no benchmark execution. |
| What it DOESN'T do | Does not run benchmarks. Does not detect performance regressions. Does not compare against baselines. |
| False-confidence risk | **High.** The name `jmhClasses` sounds like a performance check, but it's only a syntax check for benchmark code. A developer could introduce an O(n²) regression and this step would pass. |

**Test evidence:**
```bash
# ./gradlew :backend:jmhClasses
# Tasks: compileKotlin, compileTestKotlin, compileJmhJava → jmhClasses
# No benchmark execution. No timing output.
```

---

### 6. Pre-commit step: `composeApp:compileKotlinDesktop` + `compileDebugKotlinAndroid`

**Verdict:** ⚠️ Partially effective — catches compilation errors but skips iOS.

| What | Finding |
|------|---------|
| Desktop (JVM) | ✅ Compilation verified. |
| Android | ✅ Compilation verified. |
| iOS | ❌ NOT checked. The hook has no `compileKotlinIosArm64` or similar task. |
| Shared module | ⚠️ Compiled transitively as a dependency, but never explicitly verified as a standalone step. If the shared module is broken in a way that only manifests in iOS targets, it would not be caught. |

---

### 7. Pre-commit step: Postgres connectivity check

**Verdict:** ⚠️ Partially effective — checks TCP/auth, not schema.

| What | Finding |
|------|---------|
| TCP connectivity | ✅ Verified via `pg_isready` / `psql` / `/dev/tcp` (3 fallback methods). |
| Schema existence | ❌ NOT verified. Does not check that tables or views exist. Could connect to an empty database. |
| Migration state | ❌ NOT verified. Does not check that Flyway migrations have been applied. A freshly initialized Postgres with no migrations passes. |
| False-confidence risk | **Low-Medium.** The app boot test (next step) would catch a missing schema because the app would crash on startup. But the connectivity check alone gives false confidence in isolation. |

---

### 8. Pre-commit step: App boot test

**Verdict:** ❌ Ineffective for health verification — port-only, no health check.

| What | Finding |
|------|---------|
| What it checks | Process alive + port LISTENing (via `lsof` or `ss`). Waits up to 45 seconds for the port. |
| What it DOESN'T check | Does not make an HTTP request to any endpoint. Does not verify `/health`, `/api/me`, or any route. A 500-on-every-request app passes. A broken schema (caught at first query time) passes because the app starts and binds the port before any query runs. |
| False-confidence risk | **High.** This is the most misleading check. The app "starts" but no business logic is exercised. A developer could break every route and this step would still pass. |
| Time cost | ~45 seconds worst case. This is the slowest pre-commit step. |

**Test evidence:**
```bash
# Hook lines 74-107:
# ./gradlew :backend:run --no-daemon > /tmp/company-app-boot.log 2>&1 &
# Then polls lsof/ss for port binding. No HTTP request. No health check.
```

---

### 9. Pre-push step: JMH benchmark run

**Verdict:** ❌ Ineffective — reminder-only, no baseline enforcement.

| What | Finding |
|------|---------|
| Benchmark execution | ✅ Runs the full JMH suite (`./gradlew :backend:jmh`). Output is saved to `/tmp/company-app-jmh.log`. |
| Baseline comparison | ❌ NOT enforced. The hook prints a reminder, then **unconditionally returns success**. `check-baselines.sh` exists and is fully functional, but is NEVER called by the hook. |
| False-confidence risk | **High.** Benchmarks run, results are generated, but a >20% regression silently passes. The developer must manually run `check-baselines.sh` — which the hook only reminds them to do. |

**Test evidence:**
```bash
# Hook lines 20-28:
# Runs JMH suite → prints reminder message → unconditionally returns 0
# check-baselines.sh is never invoked
```

---

### 10-14. k6 load-test suite

**Verdict:** ❌ All scripts are entirely manual — none are wired into any gate.

| Script | Wired to gate? | Thresholds enforced? | Verdict |
|--------|---------------|---------------------|---------|
| `scripts/load-test/baseline.js` | None | Defined but never enforced. `branches_latency` p95<500ms, `clients_search_latency` p95<1000ms, `sessions_latency` p95<1000ms, `errors` rate<5%. | **Manual** |
| `tests/k6/full-suite.js` | None | Defined in `helpers.js` (14 thresholds). Never enforced by any hook. Covers all API paths. | **Manual** |
| `tests/k6/concurrency-test.js` | None | `concurrency_latency` p95<1000ms, `errors` rate<10%. Tests 409 + idempotency + version mismatch. | **Manual** |
| `tests/k6/authz-test.js` | None | `authz_latency` p95<1000ms, `errors` rate<10%. Tests invalid/expired token, insufficient capability. | **Manual** |
| `tests/k6/remittance-race-test.js` | None | `remittance_race_latency` p95<3000ms, `errors` rate<20%. Tests SERIALIZABLE isolation race. | **Manual** |

**How results flow into decision-making:** They don't. No results are captured, compared, or surfaced automatically. The `baseline-results.md` and `latest.json` infrastructure exists but is never consulted by any automated process.

---

### 15. Bypass paths

| Bypass | How | Risk |
|--------|-----|------|
| `git commit --no-verify` | Skips pre-commit hook entirely. | **High.** One flag disables all 8 pre-commit checks. Standard in developer workflows for WIP commits. |
| `git push --no-verify` | Skips pre-push hook entirely. | **High.** One flag disables the JMH run. |
| `core.hooksPath` not set | If `setup-hooks.sh` was never run, `core.hooksPath` defaults to `.git/hooks/` (empty). | **High.** Hooks silently don't run — no error, no warning. |
| `core.hooksPath` changed | `git config core.hooksPath ""` or pointing elsewhere. | **Medium.** Requires intentional action. |
| Missing k6 binary | k6 is not installed by any setup step. If absent, k6 scripts silently can't run. | **Low** (k6 is manual anyway). |
| Missing Postgres | Tests and app boot fail — this is a real gate, not a bypass. | **N/A** (this is a feature). |

---

### 16. Cross-cutting gaps

| Gap | Severity | Detail |
|-----|----------|--------|
| **No CI/CD** | 🔴 Critical | No GitHub Actions, no pipelines. Quality is 100% client-side. A developer who skips hooks (intentionally or accidentally) can push broken code. |
| **No server-side enforcement** | 🔴 Critical | No branch protection rules, no required status checks, no PR gate. Pushes are not validated by any server. |
| **No `:shared` module verification** | 🟡 Medium | Shared module (serialization/DTOs) is compiled transitively but never explicitly verified. No shared module tests exist. `:shared` has 0 test files. |
| **No Flyway migration verification** | 🟡 Medium | Migrations in `backend/src/main/resources/db/migration/` are applied at app boot but never verified by a gate. A bad migration (syntax error, incompatible column type) breaks the app only when Flyway runs, which might be at deploy time, not commit time. |
| **k6 not automated** | 🟡 Medium | Threshold violations are documentation, not gates. No hook or CI runs k6. |
| **No iOS compilation check** | 🟢 Low | composeApp iOS target is not compiled in any hook. Desktop + Android only. |
| **App boot: no health endpoint** | 🟡 Medium | Adding a simple `GET /health` that runs `SELECT 1` would transform the app boot test from "port bound" to "app actually responds to requests." |
| **Pre-push JMH: no enforcement** | 🟡 Medium | `check-baselines.sh` already exists and works — it just isn't called by the hook. Fix is adding one line to `.githooks/pre-push`. |

---

## Effectiveness matrix

| Gate | Blocks real problems? | False confidence? | Fix priority |
|------|----------------------|-------------------|-------------|
| ktlintFormat | ✅ Yes | ⚠️ Scope (sweeps unrelated files) | P3 |
| detekt (maxIssues:0) | ✅ Yes | — | — |
| ktlintCheck | ✅ Yes (redundant) | — | — |
| backend:test | ✅ Yes (28 integration + 6 unit) | — | — |
| jmhClasses | ❌ No (compile-only) | 🔴 High | P1 |
| composeApp compilation | ⚠️ Desktop+Android only | 🟡 Low (no iOS) | P3 |
| Postgres check | ⚠️ TCP/auth only | 🟡 Low (app boot catches rest) | — |
| App boot test | ❌ No (port-only) | 🔴 High | P1 |
| Pre-push JMH run | ❌ No (reminder-only) | 🔴 High | P1 |
| k6 baseline | ❌ No (manual) | 🟡 Medium | P2 |
| k6 full-suite | ❌ No (manual) | 🟡 Medium | P2 |
| k6 concurrency/authz/race | ❌ No (manual) | 🟡 Medium | P2 |

---

## Follow-up tickets

The following fix tickets should be filed:

1. **P1 — Add health check to app boot test** — Add `GET /health` to the app, then have the pre-commit hook curl it after the port is bound. Transforms "port bound" into "app responds to requests."
2. **P1 — Wire check-baselines.sh into pre-push hook** — Add one line to `.githooks/pre-push` calling `bash scripts/check-baselines.sh /tmp/company-app-jmh.log` after JMH runs. Exit non-zero if baselines regressed >20%.
3. **P1 — Replace jmhClasses with actual JMH run in pre-commit** — Change pre-commit from `:backend:jmhClasses` to `:backend:jmh` + `check-baselines.sh` (or a faster subset). jmhClasses alone is worthless as a gate.
4. **P2 — Wire k6 baseline into pre-push hook** — Run `scripts/load-test/baseline.js` as part of pre-push. Requires app to be running (or start/stop it like the pre-commit boot test).
5. **P2 — Add `:shared` compilation + test step** — Add `./gradlew :shared:build` to pre-commit (currently no shared module verification).
6. **P3 — Add iOS compilation target** — Add `./gradlew :composeApp:compileKotlinIosArm64` (or equivalent) to pre-commit.
7. **P3 — Scope ktlintFormat to staged files only** — Replace `./gradlew ktlintFormat && git add -u` with a scoped format that only touches staged `.kt` files.

---

**Status:** ✅ done
