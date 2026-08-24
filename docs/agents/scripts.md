# Scripts inventory

What each script under `scripts/` exists for, so a session reaches for an existing tool
instead of rebuilding it. Invocation detail lives in each script's header comments — read
them before first use.

## Self-test convention

`<name>-test.sh` files are self-tests for their sibling (`check-baselines-test.sh` guards
`check-baselines.sh`, …). Plain bash fixtures — no Gradle, database, or network. Run the
relevant self-test whenever you edit its sibling.

## Validation and diagnostics

| Script | Purpose |
|---|---|
| `validate.sh` | Targeted-validation entry point (see AGENTS.md Commands). Auto mode classifies changed files into the narrowest warm Gradle set (focused `--tests` filters for changed test files; docs-only changes skip builds entirely). Passthrough mode runs any gradle args given. Agent-invoked only — hooks never call it. |
| `local-ci.sh` | Replicates the hosted `quality.yml` gate set locally, detached: quality tasks verbatim, then cleanliness, OpenAPI, and the compose-compile matrix (Android leg skips without an SDK). `--status` prints per-gate PASS/FAIL/SKIP/RUNNING; state under gitignored `logs/local-ci/`. Opt-in diagnostic, never a gate. |
| `run-k6-contract-suites.sh` | Boots the backend on the **test** DB (never the app DB; port 8180 default, refuses a busy port) with DevSeeder provisioning both the GLOBAL owner and — when `SCOPED_USERNAME`/`SCOPED_PASSWORD` are set (#411) — the branch-scoped principal that drives the authz 403 contract and full-suite's scoped leg group, then runs every k6 suite — remittance-race first (it needs a fresh clock-in; strict 201 vs the others' 409 tolerance). Failure-safe cleanup always restores test-DB cleanliness. Manual diagnostic when load/contract behavior is the ticket's actual question. |
| `check-baselines.sh` | Compares JMH output (default `/tmp/company-app-jmh.log`, `BASELINE` env overrides the baseline file) against `backend/jmh-baselines.md`. Exit 1 = regression past threshold (20% default, 40% for `BranchDayBenchmark.*` noise-class); exit 2 = missing/malformed/incomplete benchmark evidence, which also fails. New benchmarks report NEW, not fail. Companion to `./gradlew :backend:jmh`. |

## Test database

Shared helpers for both live in `lib/common.sh`: `test_db_name` (explicit `TEST_DB_NAME`
or `<POSTGRES_DB>_test`), `test_data_tables` (non-seed base-table discovery; fails closed
on unsafe identifiers or unreadable DB — unreadable must never look clean),
`test_db_psql` (docker-exec transport when `TEST_DB_CONTAINER` set or host psql absent).

| Script | Purpose |
|---|---|
| `check-test-cleanliness.sh` | Asserts zero leftover rows in any test-managed table post-suite (seed tables `role`/`capability`/`role_capability` + Flyway metadata excepted). The CI cleanliness step. |
| `clean-test-db.sh` | Truncates user-data tables preserving seeds, then re-runs the cleanliness check as proof. Run after k6 sessions or before rerunning contaminated focused tests. |

## OpenAPI contract gate

Pipeline: Kotlin compile (kapt emits the spec) → `normalize-openapi-spec.mjs` →
`verify-openapi-spec.sh`. The normalizer binds every operation to its exact route
registration + handler source (hashed, ranged `x-route-source`) and its exact source
`@OpenApi` annotation (`x-openapi-source`), traces handler/service/mapper code to derive
response schemas, request bodies, query params, and error responses, and finally compares
the whole route-contract fingerprint against `openapi-route-contract.json`. The verifier
re-parses the Kotlin sources and asserts coverage, bearer security, operationId hygiene,
ErrorResponse/bodyless rules, schema resolution, binding freshness, and scans for leaked
secret names. Deliberately off every compile path so JVM-only Docker builders can
`:backend:installDist` without Node (#372).

| File | Purpose |
|---|---|
| `check-openapi-spec.sh` | Full local gate (the verbatim CI step): compile + publish spec, verify, then two negative controls — a drifted spec and a stale fingerprint must both fail closed. Emits the `OPENAPI_*_OK` markers. |
| `verify-openapi-spec.sh` | Verifier over a normalized spec; freshness check (spec newer than all inputs) skippable via `OPENAPI_VERIFY_SKIP_FRESHNESS=1`. |
| `normalize-openapi-spec.mjs` | Normalization + fingerprint computation. Stale fingerprint throws unless `UPDATE_OPENAPI_ROUTE_CONTRACT=1` (the documented refresh flow). |
| `openapi-source-parser.mjs` | Comment-stripping, delimiter-balancing, and `@OpenApi` annotation parsing helpers shared by normalizer and verifier. |
| `openapi-route-contract.json` | Stored fingerprint state consumed by normalizer + verifier. Changed only via the `UPDATE_OPENAPI_ROUTE_CONTRACT=1` flow, never by hand. |

## Git hooks and their guards

Hooks live in `.githooks/`; `setup-hooks.sh` installs them plus the standalone ktlint CLI.
Design invariant: near-zero cost — no Gradle, DB, containers, network clients, or k6 ever.

| Script | Guards |
|---|---|
| `setup-hooks.sh` | Installs `.githooks/*` + standalone ktlint CLI. Run once after cloning. |
| `pre-commit-docs-only-test.sh` | pre-commit still runs its cheap checks on docs-only staging (the deleted docs-only fast path must stay dead). |
| `pre-commit-formatter-status-test.sh` | A failing ktlint formatter run propagates nonzero. |
| `test-commit-msg-hook.sh` | commit-msg `ref #n` acceptance rules (present/multiple/closed-tense) + merge-commit exemption. |
| `test-hooks-no-expensive-commands.sh` | Failing shims prove pre-commit/pre-push never invoke gradle/psql/docker/k6/curl/wget/pg_isready; negative controls replay the retired gates-heavy hooks. |

## Wayfinder operations

| Script | Purpose |
|---|---|
| `wayfinder-create-child.sh` | Creates a child issue of a map with native sub-issue attachment, parent-link verification, and its `wayfinder:<type>` label — the only sanctioned path for children. Prints the URL. |
| `wayfinder-verify-child.sh` | Asserts a child's native parent link points at the expected map and carries a `wayfinder:` label. |
| `wayfinder-park.sh` | Parks uncommitted mid-ticket work (`git stash -u`, canonical message, prints the stash line to record in the handoff packet). The chain's spawn gate requires a clean worktree; parking beats a silent forever-pause. |
| `wayfinder-loop.sh` | Unattended chain daemon: watches `.wayfinder/handoffs/` (content-hash tracked), spawns each successor session with `/wayfinder <packet>`, supervises it (question/permission pings, immediate-stop + zombie resume, bounded 2-retry pause-and-page, unbounded recovery only for truncated-provider errors), gates spawns on a clean worktree and a free-disk floor. Never stages or commits (`test-wayfinder-loop-no-git-writes.sh` guards that). Modes: `--bootstrap <doc>` first start, plain restart, `--resume <sid>` in-place, `--retry` fresh respawn of a confirmed-gone session. |
| `test-wayfinder-loop-no-git-writes.sh` | Recording git shim proves the daemon bootstrap/spawn paths issue no `add`/`commit`/stash-like commands; dirty tree pauses instead of auto-committing. |

## Environment hygiene

| Script | Purpose |
|---|---|
| `vps-migration-wizard.sh` | Interactive wizard, generated by `/wizard`: walks the human through provisioning an Oracle Cloud VPS end-to-end — VM creation, Tailscale, base packages + Android SDK, opencode2 service, repo clone + secrets, Postgres 18, hooks + warm build, firewall/SSH hardening, Coolify install and app deployment, then daemon migration and post-migration checks. Re-run safe (saved answers in `.env`-style state); do not hand-edit the library section above the STAGES marker. |
| `tmp-bun-so-clean.sh` | Deletes orphaned Bun `.so` extraction temp files from `/tmp` (older than 10 min, not held open). Recovery for the /tmp-fill class the wayfinder-loop disk-floor tripwire detects; safe to rerun anytime. |

## Shared library

`lib/common.sh` — sourced by project scripts and hooks: timestamped `log`, repo-root
discovery (`ensure_root_dir`), silent `.env` sourcing, test-DB naming/discovery/psql
transport (host or docker-exec), SQL identifier quoting, port-liveness probe, graceful
SIGTERM→SIGKILL process cleanup.
