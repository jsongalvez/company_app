# Handoff — Map #329, Session 379 (ticket #335)

## Authority

- **Map #329 is active** ([Map: Agent throughput — remove blocking validation and integration latency](https://github.com/jsongalvez/company_app/issues/329)). Supersedes #305/#308/#309/#312 integration policy where conflicting.
- **Hydration invariant (map body):** `/wayfinder <handoff>` is pointer-only. Before planning/editing/resolving, fetch the active ticket's **body + all comments** from GitHub (chronological); fetch parent map body for sequencing; GitHub state beats handoff text; durable map policy lives in the map **body**.
- **Authority policies (map body):** Detekt = rule parity, execution non-parity; GitHub-hosted Actions = budgeted, **≤300 min/month** cap recorded in root AGENTS.md (quality ≤240, jmh ≤55 manual).
- **Owner live policy:** direct-to-master AFK work, no PRs unless asked, zero CI polling. Next-session CI reconciliation = one unbilled `gh api repos/jsongalvez/company_app/commits/<latest-master-sha>/check-runs` call (procedure in root AGENTS.md "Performance").

## Work completed (ticket #335 — resolved, closed)

- Verified blocking edge `scripts/wayfinder-verify-child.sh 329 335`, claimed, resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/335
- Reconciliation first: latest master (`bebc6650`) async quality run green — no repair needed.
- Live k6/e2e surface was mostly conforming already (hooks + quality.yml clean from #333). Fixed the residue in commit `efcd8e6d` (direct-to-master, pushed):
  - `scripts/run-k6-contract-suites.sh` header: was citing the **deleted** `.github/workflows/k6.yml` and calling the script "the k6 gate" → now manual diagnostic (#333/#335), hooks/CI never run it.
  - `docs/specs/0001-frontend-rebuild.md`: k6 full-suite was an unconditional post-change regression gate → manual diagnostic; focused Gradle tests own backend validation.
  - `docs/adr/0006-test-database-isolation.md`: claimed the cleanliness check wired into pre-commit + pre-push (before the k6 baseline) → now quality.yml + local only, hooks never run it.
  - `README.md`: k6 install marked optional (manual load tests only).
- Validation: `bash -n` on the script; `bash scripts/validate.sh` → docs-only path. No Kotlin; no hosted Actions consumed (push touched no quality.yml trigger path).
- Map Decisions-so-far gained the #335 line (body PATCHED 2026-08-21).

## Tracker state

- Frontier now (both unblocked): **[Throughput: demote JMH to non-blocking diagnostics](https://github.com/jsongalvez/company_app/issues/334)** (small residue: `jmh.yml` header still carries pre-#334 "gate" language + baseline-median instructions that partially conflict with the dispatch-only reality; remaining: docs/baseline-policy sweep) and **[Throughput: ephemeral handoffs + no daemon auto-commits](https://github.com/company_app/issues/336)** (unblocks #337).
- #337 waits on #336; #339 waits on #334 + #335 (done) + #337 + #338.
- Prefer **#336** next: it unblocks #337, and #337+#339 are the remaining deletion-heavy closeout tickets. #334 is a small docs sweep any time after.

## Next action

1. Hydrate: read map #329 body (+ new comments) and chosen child's body + all comments before claiming.
2. **First: run the reconciliation call** (root AGENTS.md) against `efcd8e6d`'s async state — note this push touched no quality trigger paths, so expect no new runs; red = repair before new work.
3. `bash scripts/wayfinder-verify-child.sh 329 <child>`, claim exactly one frontier child, resolve, write successor handoff.
4. Direct-to-master; smallest warm validation (`bash scripts/validate.sh`); no CI polling.
5. Environment unchanged: root `.env` present, Postgres up via docker compose, k6 CLI local, APP_PORT 8180 locally (8080 coolify proxy), ktlint 1.8.0 cached at `~/.cache/company-app/ktlint/`, Gradle daemon warm.

Stop. Session quota spent (one ticket resolved).
