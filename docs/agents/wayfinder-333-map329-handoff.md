# Handoff — Map #329, Session 378 (ticket #333)

## Authority

- **Map #329 is active** ([Map: Agent throughput — remove blocking validation and integration latency](https://github.com/jsongalvez/company_app/issues/329)). Supersedes #305/#308/#309/#312 integration policy where conflicting.
- **Hydration invariant (map body):** `/wayfinder <handoff>` is pointer-only. Before planning/editing/resolving, fetch the active ticket's **body + all comments** from GitHub (chronological); fetch parent map body for sequencing; GitHub state beats handoff text; durable map policy lives in the map **body**.
- **Authority policies (map body):** Detekt = rule parity, execution non-parity; GitHub-hosted Actions = budgeted, **≤300 min/month** cap recorded in root AGENTS.md (quality ≤240, jmh ≤55 manual).
- **Owner live policy:** direct-to-master AFK work, no PRs unless asked, zero CI polling. Next-session CI reconciliation = one unbilled `gh api repos/jsongalvez/company_app/commits/<latest-master-sha>/check-runs` call (procedure in root AGENTS.md "Performance").

## Work completed (ticket #333 — resolved, closed)

- Verified blocking edge `scripts/wayfinder-verify-child.sh 329 333`, claimed, resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/333 (closed with it).
- **Measured before redesign:** 927 hosted min over last 2 days (quality 566, jmh 250, openapi 91, k6 20) — ~14k/min-month pace vs 2,000 allowance.
- **openapi.yml deleted** → absorbed into quality.yml (one checkout/JDK/Gradle setup pays for OpenAPI contract too).
- **k6.yml deleted** (broad baseline auto-ran on `tests/k6/**` pushes — §7 violation; #335 owns k6 demotion).
- **jmh.yml** push trigger → `workflow_dispatch` only (#334 owns remaining demotion).
- **quality.yml** triggers narrowed to master pushes + PRs with code-path matches (was: any branch); `--no-daemon` dropped; compose matrix retained (2 jobs, platform isolation).
- **Hosted repair workflow drafted then deleted** (`86d2ee26` added it, `ab0539e7` removed it): it raced parallel quality runs (null conclusions = false red) and would fire on docs-only pushes. Red run itself is the durable signal.
- **Budget:** ≤300 hosted Actions min/month written into root AGENTS.md Performance section; next-session reconciliation procedure documented there.

## Integration evidence

- Commits `86d2ee26`, `ab0539e7` direct-to-master, pushed immediately. Net −149 lines across the two.
- Change-type = workflows + docs → `validate.sh` docs-only path (no Gradle); workflow YAML validated with a PyYAML trigger parse; `bash -n scripts/validate.sh` clean. No Kotlin touched.
- Async CI: push touched `.github/workflows/**` → quality triggered (path-justified, billable, ~25 min). **Not polled**; next session reconciles via the check-runs API call above before claiming work — red = repair first.

## Tracker state

- #333 closed with resolution; map Decisions-so-far gained the #333 line (body PATCHed 2026-08-21T09:03:56Z).
- Frontier now (all unblocked): **[Throughput: demote JMH to non-blocking diagnostics](https://github.com/jsongalvez/company_app/issues/334)** (jmh trigger work already done; remaining: docs/baseline-policy sweep), **[Throughput: remove broad k6/e2e suites from the automatic path](https://github.com/jsongalvez/company_app/issues/335)** (k6.yml already deleted; remaining: local-script/doc sweep), **[Throughput: ephemeral handoffs + no daemon auto-commits](https://github.com/jsongalvez/company_app/issues/336)**, and **[Throughput: use global skills only and remove repo-local skill sources](https://github.com/jsongalvez/company_app/issues/338)** (any-time). #337 waits on #336; #339 waits on all.
- Prefer #335 or #336 next: #334's heavy lifting (trigger removal) landed early under #333, so its residue is small; #335 has the largest remaining local-path surface.

## Next action

1. Hydrate: read map #329 body (+ new comments) and chosen child's body + all comments before claiming.
2. `bash scripts/wayfinder-verify-child.sh 329 <child>`, claim exactly one frontier child, resolve, write successor handoff.
3. **First: run the reconciliation call** (root AGENTS.md) against `ab0539e7`'s async quality run; red = repair before new work.
4. Direct-to-master; smallest warm validation (`bash scripts/validate.sh`); no CI polling.
5. Environment unchanged: root `.env` present, Postgres up via docker compose, k6 CLI local, APP_PORT 8180 locally (8080 coolify proxy), ktlint 1.8.0 cached at `~/.cache/company-app/ktlint/`, Gradle daemon warm.

Stop. Session quota spent (one ticket resolved).
