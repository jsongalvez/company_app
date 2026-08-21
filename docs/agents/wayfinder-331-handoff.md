# Handoff — Map #329, Session 376

## Authority

- **Map #329 is active** ([Map: Agent throughput — remove blocking validation and integration latency](https://github.com/jsongalvez/company_app/issues/329)). Supersedes #305/#308/#309/#312 integration policy where conflicting.
- **Hydration invariant (map body):** `/wayfinder <handoff>` is pointer-only. Before planning/editing/resolving, fetch the active ticket's **body + all comments** from GitHub (chronological); fetch parent map body for sequencing; GitHub state beats handoff text; durable map policy lives in the map **body**.
- **Authority policies (map body):** Detekt = rule parity, execution non-parity; GitHub Actions minutes = constrained monthly budget (#333 must state explicit cap before closeout).
- **Owner live policy:** direct-to-master AFK work, no PRs unless asked, zero CI polling. Do not use `scripts/wayfinder-ci.sh` (#332 owns retiring it).

## Work completed (ticket #331 — resolved, closed)

- Verified blocking edge `scripts/wayfinder-verify-child.sh 329 331`, claimed, resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/331#issuecomment-5367421912
- **`scripts/validate.sh` (new):** classifies changed files → narrowest warm Gradle tasks in one invocation. backend→`:backend:compileKotlin`; shared→`:shared:compileKotlinJvm :shared:jvmTest`; compose→`:composeApp:compileKotlinDesktop :composeApp:desktopTest`; build-logic/detekt-config→one compile per module (+module Detekt when detekt config changed); docs-only→exit 0 no build. New/changed `*Test.kt` add focused `--tests '<fqcn>'`. Explicit args passthrough. Timing line on success.
- **All 4 `--no-daemon` removed:** check-openapi-spec.sh, start-app.sh ×2, run-k6-contract-suites.sh. Daemon + config/build caches persist.
- **Integration-gate policy language removed:** root AGENTS.md graph mechanics ("one full compile/test gate at integration" → targeted checks + async CI), code-review-loop.md review-graph paragraph, gates.md implement sequence step 3.
- **backend/AGENTS.md:** 24-task sweep block replaced by change-type→task table (all six acceptance examples covered); opt-in broad sweep retained; DB-cleanliness paragraph de-gated ("full backend gate" → focused rerun).
- **composeApp/AGENTS.md:** new Validation section — desktop-target-only default, broader targets risk-based.
- **Root AGENTS.md Commands:** validate.sh added; `ktlintCheck` + backend quality-gate trio removed from guidance; JMH marked manual diagnostics.

## Integration evidence

- Commit `6c4b6969` direct-to-master, pushed (pre-commit ~0.4s, pre-push bookkeeping-only).
- Behavioral controls: negative (junk .kt → correct task selected, exit 1), positive (shared probe → warm BUILD SUCCESSFUL 57s incl. cached jvmTest), docs-only (exit 0). Guard-rail `test-hooks-no-expensive-commands.sh` PASS.
- Async CI note: push touches `scripts/**` + agent docs → quality.yml likely triggered (billable). Not polled; red = next-session priority per constitution §10.

## Tracker state

- #331 closed with resolution. Map Decisions-so-far gained #331 line.
- Frontier now: **[Throughput: make direct-to-master the default AFK integration path](https://github.com/jsongalvez/company_app/issues/332)** (only blocker #331 verified closed; execution-order step 3) and **[Throughput: use global skills only and remove repo-local skill sources](https://github.com/jsongalvez/company_app/issues/338)** (any-time child). Prefer #332 (critical path).

## Next action

1. Hydrate: read map #329 body (+ new comments) and #332 body + all comments before claiming.
2. `bash scripts/wayfinder-verify-child.sh 329 332`, claim exactly one frontier child (recommended: #332), resolve, write successor handoff.
3. Direct-to-master; smallest warm validation that answers the implementation question (`bash scripts/validate.sh`); no CI polling.
4. Environment unchanged: root `.env` present, Postgres up via docker compose, k6 CLI local, APP_PORT 8180 locally (8080 coolify proxy), ktlint 1.8.0 cached at `~/.cache/company-app/ktlint/`, Gradle daemon warm.

Stop. Session quota spent (one ticket resolved).
