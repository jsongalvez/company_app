# Handoff — Map #329, Session 377 (ticket #332)

## Authority

- **Map #329 is active** ([Map: Agent throughput — remove blocking validation and integration latency](https://github.com/jsongalvez/company_app/issues/329)). Supersedes #305/#308/#309/#312 integration policy where conflicting.
- **Hydration invariant (map body):** `/wayfinder <handoff>` is pointer-only. Before planning/editing/resolving, fetch the active ticket's **body + all comments** from GitHub (chronological); fetch parent map body for sequencing; GitHub state beats handoff text; durable map policy lives in the map **body**.
- **Authority policies (map body):** Detekt = rule parity, execution non-parity; GitHub Actions minutes = constrained monthly budget (#333 must state explicit cap before closeout).
- **Owner live policy:** direct-to-master AFK work, no PRs unless asked, zero CI polling. `scripts/wayfinder-ci.sh` is now **deleted** (#332); nothing enforces `ralph/wayfinder-*` branches anymore.

## Work completed (ticket #332 — resolved, closed)

- Verified blocking edge `scripts/wayfinder-verify-child.sh 329 332`, claimed, resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/332#issuecomment-5367531155
- **Deleted:** `scripts/wayfinder-ci.sh` (branch/PR/CI-wait ceremony), `scripts/wayfinder-afk-test.sh` (its harness), `docs/gates/312-wayfinder-afk.md` (ledger pointing at both).
- **Root AGENTS.md:** new "Integration — direct-to-master" section — ordinary AFK tickets commit straight to master and push immediately; PR escalation triggers enumerated (human request / external contributor / unusual risk / future multi-agent); high-risk stays a review-profile matter, never "wait on CI"; unrelated dirty work still refused; no long-lived integration branches.
- **Incidental root-cause fix:** `.githooks/pre-commit` crashed on commits deleting staged shell files (`bash -n` on a nonexistent path). Now filters to files present on disk.

## Integration evidence

- Commit `fce2bb31` direct-to-master, pushed immediately (dogfood of the new default; pre-push bookkeeping-only).
- `scripts/validate.sh`: docs-only → exit 0 no build. `scripts/test-hooks-no-expensive-commands.sh` PASS post-hook-change. `bash -n scripts/wayfinder-loop.sh` clean.
- Async CI note: push touches `.githooks/**`, `scripts/**`, agent docs → quality.yml likely triggered (billable). Not polled; red = next-session priority per constitution §10.

## Tracker state

- #332 closed with resolution; map Decisions-so-far gained the #332 line.
- Frontier now: **[Throughput: make CI asynchronous and repair failures next session](https://github.com/jsongalvez/company_app/issues/333)** (critical path, execution-order step 4) and **[Throughput: use global skills only and remove repo-local skill sources](https://github.com/jsongalvez/company_app/issues/338)** (any-time child). Prefer #333.
- Blocked behind #333: #334, #335, #336 (then #337); #339 waits on all.

## Next action

1. Hydrate: read map #329 body (+ new comments) and #333 body + all comments before claiming.
2. `bash scripts/wayfinder-verify-child.sh 329 333`, claim exactly one frontier child (recommended: #333), resolve, write successor handoff.
3. Direct-to-master; smallest warm validation (`bash scripts/validate.sh`); no CI polling.
4. Environment unchanged: root `.env` present, Postgres up via docker compose, k6 CLI local, APP_PORT 8180 locally (8080 coolify proxy), ktlint 1.8.0 cached at `~/.cache/company-app/ktlint/`, Gradle daemon warm.

Stop. Session quota spent (one ticket resolved).
