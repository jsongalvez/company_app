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
| `local-ci.sh` | Replicates the hosted `quality.yml` gate set locally, detached: quality tasks verbatim, then OpenAPI and the compose-compile matrix (Android leg skips without an SDK). `--status` prints per-gate PASS/FAIL/SKIP/RUNNING; state under gitignored `logs/local-ci/`. Opt-in diagnostic, never a gate. |
| `run-k6-contract-suites.sh` | Boots the backend on the **test** DB (never the app DB; port 8180 default, refuses a busy port) with DevSeeder provisioning both the GLOBAL owner and — when `SCOPED_USERNAME`/`SCOPED_PASSWORD` are set (#411) — the branch-scoped principal that drives the authz 403 contract and full-suite's scoped leg group, then runs every k6 suite — remittance-race first (it needs a fresh clock-in; strict 201 vs the others' 409 tolerance). Failure-safe cleanup always restores test-DB cleanliness. Manual diagnostic when load/contract behavior is the ticket's actual question. |
| `check-baselines.sh` | Compares JMH output (default `/tmp/company-app-jmh.log`, `BASELINE` env overrides the baseline file) against `backend/jmh-baselines.md`. Exit 1 = regression past threshold (20% default, 40% for `BranchDayBenchmark.*` noise-class); exit 2 = missing/malformed/incomplete benchmark evidence, which also fails. New benchmarks report NEW, not fail. Companion to `./gradlew :backend:jmh`. |

## Test database

Shared helpers for both live in `lib/common.sh`: `test_db_name` (explicit `TEST_DB_NAME`
or `<POSTGRES_DB>_test`), `test_data_tables` (non-seed base-table discovery; fails closed
on unsafe identifiers or unreadable DB — unreadable must never look clean),
`test_db_psql` (docker-exec transport when `TEST_DB_CONTAINER` set or host psql absent).

| Script | Purpose |
|---|---|
| `check-test-cleanliness.sh` | Asserts zero leftover rows in any `public`-schema test-managed table (seed tables `role`/`capability`/`role_capability` + Flyway metadata excepted). k6/manual `public`-DB evidence only (#493) — backend workers use owned `test_w_*` schemas and never touch `public` (lifecycle proof: `WorkerSchemaLifecycleTest`). |
| `clean-test-db.sh` | Truncates `public` user-data tables preserving seeds, then re-runs the cleanliness check as proof. Run after k6 sessions. Backend focused tests need no cleanup — each worker mints a fresh owned schema per JVM. |

## OpenAPI contract gate (#495 contract, #496 old-pipeline deletion)

Pipeline: Kotlin compile (kapt emits the spec) → `OpenApiCanonical.canonicalize`
(replaces DTO component schemas from actual serializer descriptors) → served by
production `OpenApiPlugin` and the offline export identically. No Node, no
source parsing, no fingerprint state, no freshness/mtime checks: inputs are
compiled classes/resources plus serializer metadata. Deliberately off every
compile/installDist path so JVM-only Docker builders stay Node-free (#372).

| Task | Purpose |
|---|---|
| `./gradlew :backend:verifyOpenApiContract` | Sole contract gate (the verbatim CI step): DB-free `OpenApiContractTest` over the kapt resource through the canonical transform. Failing contract tests fail this check. |
| `./gradlew :backend:exportOpenApiSpec` | Writes the canonical document to `backend/build/openapi/openapi-canonical.json` (build artifact, never committed) for import into API clients — see `docs/api.md`. |

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

Canonical owner: `tools/wayfinder/`. `scripts/wayfinder-*.sh` are thin exec
wrappers for the running daemon and external launchers; new invocations should
prefer the canonical path.

| Script | Purpose |
|---|---|
| `tools/wayfinder/wayfinder-create-child.sh` | Creates a child issue of a map with native sub-issue attachment, parent-link verification, and its `wayfinder:<type>` label — the only sanctioned path for children. Prints the URL. |
| `tools/wayfinder/wayfinder-verify-child.sh` | Asserts a child's native parent link points at the expected map and carries a `wayfinder:` label. |
| `tools/wayfinder/wayfinder-park.sh` | Parks uncommitted mid-ticket work (`git stash -u`, canonical message, prints the stash line to record in the handoff packet). The chain's spawn gate requires a clean worktree; parking beats a silent forever-pause. |
| `tools/wayfinder/wayfinder-loop.sh` | Unattended chain daemon: watches `.wayfinder/handoffs/` (content-hash tracked), spawns each successor session with `/wayfinder <packet>`, supervises it (question/permission pings, immediate-stop + zombie resume, bounded 2-retry pause-and-page, unbounded recovery only for truncated-provider errors), gates spawns on a clean worktree and a free-disk floor. Never stages or commits (`test-wayfinder-loop-no-git-writes.sh` guards that). Modes: `--bootstrap <doc>` first start, plain restart, `--resume <sid>` in-place, `--retry` fresh respawn of a confirmed-gone session. |
| `tools/wayfinder/test-wayfinder-loop-no-git-writes.sh` | Recording git shim proves the daemon bootstrap/spawn paths issue no `add`/`commit`/stash-like commands; dirty tree pauses instead of auto-committing. Covers both canonical and wrapper entrypoints. |
| `tools/wayfinder/test-wayfinder-local-ci-watch.sh` | Contract for the detached local-CI pin/watch path: HEAD pin stability, queued relaunch, verdict dedupe, dry-run planning, and red-verdict repair gating. |
| `tools/wayfinder/recovery-contract.test.sh` | Structural + behavioral contract for the recovery prompt, retry guard, transient-error nudges, and progress-budget reset; also proves the loop wrapper delegates. |
| `tools/wayfinder/wrapper-contract.test.sh` | Proves every `scripts/wayfinder-*` wrapper exec-delegates with arg/status/signal propagation and reaches the same endpoints from any cwd. |

## Environment hygiene

| Script | Purpose |
|---|---|
| `vps-migration-wizard.sh` | Interactive wizard, generated by `/wizard`: walks the human through provisioning an Oracle Cloud VPS end-to-end — VM creation, Tailscale, base packages + Android SDK, opencode2 service, repo clone + secrets, Postgres 18, hooks + warm build, firewall/SSH hardening, Coolify install and app deployment, then daemon migration and post-migration checks. Re-run safe (saved answers in `.env`-style state); do not hand-edit the library section above the STAGES marker. |
| `tmp-bun-so-clean.sh` | Host maintenance (not Wayfinder-owned): deletes orphaned Bun `.so` extraction temp files from `/tmp` (older than 10 min, not held open). Recovery for the /tmp-fill class the wayfinder-loop disk-floor tripwire detects; safe to rerun anytime. Stays in `scripts/` — checked callers show no automatic invocation, only manual recovery. |

## Shared library

`lib/common.sh` — sourced by project scripts and hooks: timestamped `log`, repo-root
discovery (`ensure_root_dir`), silent `.env` sourcing, test-DB naming/discovery/psql
transport (host or docker-exec), SQL identifier quoting, port-liveness probe, graceful
SIGTERM→SIGKILL process cleanup.
