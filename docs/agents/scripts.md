# Scripts inventory

What each script under `scripts/` exists for, so a session reaches for an existing tool
instead of rebuilding it. Invocation detail lives in each script's header comments — read
them before first use.

## Self-test convention

`<name>-test.sh` files are self-tests for their sibling (`check-baselines-test.sh` guards
`check-baselines.sh`, …). Plain bash fixtures — no Gradle, database, or network. Run the
relevant self-test whenever you edit its sibling.

## Validation and diagnostics

Canonical owner: `tools/quality/`. `scripts/validate.sh`,
`scripts/inspect.sh`, and `scripts/setup-hooks.sh` are thin exec wrappers for
human/external callers; new invocations should prefer the canonical path.

| Script | Purpose |
|---|---|
| `tools/quality/validate.sh` | Targeted-validation entry point (see AGENTS.md Commands). Auto mode classifies changed files into the narrowest warm Gradle set (focused `--tests` filters for changed test files; shell/tool-only changes run `bash -n` plus the families' DB-free self-tests; docs-only changes skip builds entirely). `detekt-rules/*` and module `*/build.gradle.kts` classify as build logic. Passthrough mode runs any gradle args given. Agent-invoked only — hooks never call it. |
| `tools/quality/run-k6-contract-suites.sh` | Boots the backend on the **test** DB (never the app DB; port 8180 default, refuses a busy port) with DevSeeder provisioning both the GLOBAL owner and — when `SCOPED_USERNAME`/`SCOPED_PASSWORD` are set (#411) — the branch-scoped principal that drives the authz 403 contract and full-suite's scoped leg group, then runs every k6 suite — remittance-race first (it needs a fresh clock-in; strict 201 vs the others' 409 tolerance). Failure-safe cleanup always restores test-DB cleanliness. Manual diagnostic when load/contract behavior is the ticket's actual question. |
| `tools/quality/inspect.sh` | Static-analysis reproduction commands (#532, map #531 Phase A): `detekt`, `compiler`, `parity`, `ide-profile`, `all` — same committed config/profile CI uses. Full multi-platform Detekt/test coverage stays asynchronous CI work (#329). |
| `tools/quality/wrapper-contract.test.sh` | Proves every `scripts/*` compat wrapper exec-delegates with arg/status propagation and reaches the same endpoints from any cwd (map #533 #569). |

## Performance tooling

Canonical owner: `tools/performance/`. `scripts/check-baselines.sh` and
`scripts/run-k6-contract-suites.sh` are thin exec wrappers; new invocations
should prefer the canonical path.

| Script | Purpose |
|---|---|
| `tools/performance/check-baselines.sh` | Compares JMH output (default `/tmp/company-app-jmh.log`, `BASELINE` env overrides the baseline file) against `backend/jmh-baselines.md`. Exit 1 = regression past threshold (20% default, 40% for `BranchDayBenchmark.*` noise-class); exit 2 = missing/malformed/incomplete benchmark evidence, which also fails. New benchmarks report NEW, not fail. Companion to `./gradlew :backend:jmh`. |
| `tools/performance/check-baselines-test.sh` | Fixture suite for the baseline checker (empty/truncated/missing/malformed/complete/regressed/missing-log cases). Plain bash — no Gradle, database, or network. |
| `tools/performance/run-k6-contract-suites.sh` | Boots the backend on the **test** DB (never the app DB; port 8180 default, refuses a busy port) with DevSeeder provisioning both the GLOBAL owner and — when `SCOPED_USERNAME`/`SCOPED_PASSWORD` are set (#411) — the branch-scoped principal that drives the authz 403 contract and full-suite's scoped leg group, then runs every k6 suite — remittance-race first (it needs a fresh clock-in; strict 201 vs the others' 409 tolerance). Failure-safe cleanup always restores test-DB cleanliness. Manual diagnostic when load/contract behavior is the ticket's actual question. |
| `tools/performance/k6/` | k6 suites (`baseline`, `authz-test`, `concurrency-test`, `full-suite`, `remittance-race-test`), `helpers.js` (single source of truth for metrics/thresholds/auth), and `results/` measurement history. |

## Test database

Canonical owner: `tools/database/`. `scripts/check-test-cleanliness.sh` and
`scripts/clean-test-db.sh` are thin exec wrappers; new invocations should
prefer the canonical path.

Shared helpers live in `tools/database/lib/db-common.sh`: `test_db_name`
(explicit `TEST_DB_NAME` or `<POSTGRES_DB>_test`), `test_data_tables`
(non-seed base-table discovery; fails closed on unsafe identifiers or
unreadable DB — unreadable must never look clean), `test_db_psql`
(docker-exec transport when `TEST_DB_CONTAINER` set or host psql absent).
Generic shell mechanics (`log`, `ensure_root_dir`, `source_env`,
`port_is_listening`, `kill_cleanup`) live in
`tools/quality/lib/shell-common.sh`, sourced by `db-common.sh` — hooks and
quality scripts source `shell-common.sh` directly.

| Script | Purpose |
|---|---|
| `tools/database/check-test-cleanliness.sh` | Asserts zero leftover rows in any `public`-schema test-managed table (seed tables `role`/`capability`/`role_capability` + Flyway metadata excepted). k6/manual `public`-DB evidence only (#493) — backend workers use owned `test_w_*` schemas and never touch `public` (lifecycle proof: `WorkerSchemaLifecycleTest`). |
| `tools/database/clean-test-db.sh` | Truncates `public` user-data tables preserving seeds, then re-runs the cleanliness check as proof. Run after k6 sessions. Backend focused tests need no cleanup — each worker mints a fresh owned schema per JVM. |
| `tools/database/test-db-name-test.sh` | Fixtures for `test_db_name` derivation/override plus the k6 default. |
| `tools/database/test-db-discovery-test.sh` | Mocked-transport fixtures for `test_data_tables` discovery, quoting, empty-DB, and failure-propagation (failure-to-discover stays an error). |
| `tools/database/test-db-discovery-disposable-test.sh` | Live-DB disposable fixture: real-table discovery, `clean-test-db.sh` truncation proof, and unsafe-identifier rejection. Needs a running database and refuses the application DB — manual verification, never auto-run by `validate.sh`. |

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
| `tools/quality/setup-hooks.sh` | Installs `.githooks/*` + standalone ktlint CLI. Run once after cloning. |
| `tools/quality/pre-commit-docs-only-test.sh` | pre-commit still runs its cheap checks on docs-only staging (the deleted docs-only fast path must stay dead). |
| `tools/quality/pre-commit-formatter-status-test.sh` | A failing ktlint formatter run propagates nonzero. |
| `tools/quality/test-commit-msg-hook.sh` | commit-msg `ref #n` acceptance rules (present/multiple/closed-tense) + merge-commit exemption. |
| `tools/quality/test-hooks-no-expensive-commands.sh` | Failing shims prove pre-commit/pre-push never invoke gradle/psql/docker/k6/curl/wget/pg_isready; negative controls replay the retired gates-heavy hooks. |

## Wayfinder operations

Canonical owner: `tools/wayfinder/`. `scripts/wayfinder-*.sh` are thin exec
wrappers for the running daemon and external launchers; new invocations should
prefer the canonical path.

| Script | Purpose |
|---|---|
| `tools/wayfinder/wayfinder-create-child.sh` | Creates a child issue of a map with native sub-issue attachment, parent-link verification, and its `wayfinder:<type>` label — the only sanctioned path for children. Prints the URL. |
| `tools/wayfinder/wayfinder-verify-child.sh` | Asserts a child's native parent link points at the expected map and carries a `wayfinder:` label. |
| `tools/wayfinder/wayfinder-park.sh` | Parks uncommitted mid-ticket work (`git stash -u`, canonical message, prints the stash line to record in the handoff packet). The chain's spawn gate requires a clean worktree; parking beats a silent forever-pause. |
| `tools/wayfinder/wayfinder-worker.sh` | Herdr worker-control seam for the map chief (map #697 #735): `spawn`/`prompt`/`status`/`wait`/`read`/`stop`/`cleanup`/`reconcile` over `herdr agent` with async-by-default dispatch (no `--wait` on prompt), explicit `ticket`/`maintenance`/`bug-scout`/`helper` roles, `WAYFINDER_MAX_WORKERS` bound (default 1), chief-only orchestration gate (`WAYFINDER_ROLE`), and a TSV worker registry under gitignored `.wayfinder/`. No Git integration, no workspace creation (sibling #736). Self-test: `wayfinder-worker-test.sh`. |
| `tools/wayfinder/wayfinder-chief.sh` | Map chief scheduler (map #697 #735): reconcile → collect (`done`/`blocked` → `ready-for-review` for #740) → authoritative frontier query (open blockers, claims, `needs-info`/`ready-for-human`, priority order) → fill free capacity → idle work, looping (`--once` for a single pass, `--dry-run` side-effect free). Workers claim assign-first; the chief never assigns and never runs Git. Self-test: `wayfinder-chief-test.sh`. |
| `tools/wayfinder/wayfinder-workspace.sh` | Pluggable isolated WorkspaceProvider (map #697 #736): `create`/`inspect`/`path`/`reconcile`/`cleanup`/`list` over a provider-neutral registry (`provider`, `workspace_id`, `workspace_path`, `base_sha`, `lifecycle_state`; provider detail encapsulated). Git worktree is the default (`WAYFINDER_WORKSPACE_PROVIDER=git-worktree`, root outside the checkout, `wf/<id>` branch per workspace, deterministic force cleanup); CoW adapters land beside it without scheduler changes. Chief-only create/cleanup/reconcile (`WAYFINDER_ROLE` gate); inspect/path/list observable to all. Self-test: `wayfinder-workspace-test.sh`. |
| `tools/wayfinder/wayfinder-loop.sh` | Unattended chain daemon: watches `.wayfinder/handoffs/` (content-hash tracked), spawns each successor session with `/wayfinder <packet>`, supervises it (question/permission pings, immediate-stop + zombie resume, bounded 2-retry pause-and-page, unbounded recovery only for transient failures — truncated streams, rate-limit throttles, server-aborted steps — plus an exit-wait that holds on API outages and failed workers instead of advancing past them), gates spawns on a clean worktree and a free-disk floor. Never stages or commits (`test-wayfinder-loop-no-git-writes.sh` guards that). Modes: `--bootstrap <doc>` first start, plain restart, `--resume <sid>` in-place, `--retry` fresh respawn of a confirmed-gone session. |
| `tools/wayfinder/test-wayfinder-loop-no-git-writes.sh` | Recording git shim proves the daemon bootstrap/spawn paths issue no `add`/`commit`/stash-like commands; dirty tree pauses instead of auto-committing. Covers both canonical and wrapper entrypoints. |
| `tools/wayfinder/test-wayfinder-ci-watch.sh` | Contract for the hosted-CI repair watch: pending/unknown silence, green verdict dedupe, red-verdict single repair ticket + frontier block, dry-run side-effect freedom (all under `WAYFINDER_CI_REPAIR=on`); plus disabled-by-default silence — no polling, tickets, or verdicts. |
| `tools/wayfinder/recovery-contract.test.sh` | Structural + behavioral contract for the recovery prompt, retry guard, transient-error nudges, and progress-budget reset; also proves the loop wrapper delegates. |
| `tools/wayfinder/wrapper-contract.test.sh` | Proves every `scripts/wayfinder-*` wrapper exec-delegates with arg/status/signal propagation and reaches the same endpoints from any cwd. |

## Environment hygiene

| Script | Purpose |
|---|---|
| `vps-migration-wizard.sh` | Interactive wizard, generated by `/wizard`: walks the human through provisioning an Oracle Cloud VPS end-to-end — VM creation, Tailscale, base packages + Android SDK, opencode2 service, repo clone + secrets, Postgres 18, hooks + warm build, firewall/SSH hardening, Coolify install and app deployment, then daemon migration and post-migration checks. Re-run safe (saved answers in `.env`-style state); do not hand-edit the library section above the STAGES marker. |
| `tmp-bun-so-clean.sh` | Host maintenance (not Wayfinder-owned): deletes orphaned Bun `.so` extraction temp files from `/tmp` (older than 10 min, not held open). Recovery for the /tmp-fill class the wayfinder-loop disk-floor tripwire detects; safe to rerun anytime. Stays in `scripts/` — checked callers show no automatic invocation, only manual recovery. |

## Shared libraries

- `tools/quality/lib/shell-common.sh` — sourced by quality scripts and hooks:
  timestamped `log`, repo-root discovery (`ensure_root_dir`), silent `.env`
  sourcing, port-liveness probe, graceful SIGTERM→SIGKILL process cleanup.
- `tools/database/lib/db-common.sh` — sources `shell-common.sh` and adds
  test-DB naming/discovery/psql transport (host or docker-exec) plus SQL
  identifier quoting.
