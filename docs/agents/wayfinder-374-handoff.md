# Handoff — Map #329, Session 375

## Authority

- **Map #329 is active** ([Map: Agent throughput — remove blocking validation and integration latency](https://github.com/jsongalvez/company_app/issues/329)); #310 closed (sunset). #329 supersedes #305/#308/#309/#312 integration policy where they conflict.
- **Hydration invariant (map body, owner-added):** `/wayfinder <handoff>` is pointer-only. Before planning/editing/resolving, fetch the active ticket's **body + all comments** from GitHub (chronological); fetch parent map body for sequencing; GitHub state beats handoff text; durable map policy lives in the map **body**, not comments.
- **Authority policies (map body, "Authority policies" section):** Detekt = rule parity, execution non-parity (configs canonical, no hook/session-mandatory execution); GitHub Actions minutes = constrained monthly budget (heavy diagnostics manual/self-hosted; closeout must state an explicit cap before #317).
- **Owner live policy:** direct-to-master AFK work, no PRs unless asked, zero CI polling. `scripts/wayfinder-ci.sh` branch enforcement conflicts with this flow — do not use it; #332 owns retiring that path.

## Work completed (ticket #330 — resolved, closed)

- Verified all native blocking edges with `gh api .../dependencies` before selection (all present).
- Claimed [Throughput: make git hooks near-zero-cost](https://github.com/jsongalvez/company_app/issues/330) (`wayfinder-verify-child.sh 329 330` passed first), resolved, closed. Resolution comment: https://github.com/jsongalvez/company_app/issues/330#issuecomment-5367187313
- `.githooks/pre-commit`: staged standalone-ktlint format only (warn+skip when CLI missing; Gradle fallback deleted) + `bash -n` on staged shell files + docs-only early exit. All Gradle compile/Detekt/ktlintCheck/warnings-as-error mapping removed.
- `.githooks/pre-push`: log-only bookkeeping. Cleanliness query, OpenAPI gate, Compose compiles, backend build/boot, k6 baseline, DB cleanup, outgoing-tree classification all removed.
- `commit-msg` untouched (`ref #<number>` preserved).
- Measured: commit overhead 0.24–0.31s; push ~2s including network. Both <5s budget.
- New guard-rail test `scripts/test-hooks-no-expensive-commands.sh`: failing shims (`gradle gradlew psql docker k6 curl wget pg_isready`) prove hooks never invoke them; negative controls = synthetic gate-running hook + actual retired hooks replayed from `52d57bbf^`. Existing fixtures (docs-only, formatter-status, commit-msg) still pass.
- Docs aligned: root `AGENTS.md` hooks section, `backend/AGENTS.md` quality-gate/k6 sections, `CONTRIBUTING.md`, ADR-0010 annotation, VPS runbook + wizard de-trolled (~3-min push claims gone). Map body gained Decisions-so-far line + both authority policies.

## Integration evidence

- Four commits fast-forwarded on `master`, pushed: `52d57bbf` (hooks+test), `1119fbdf` (agent docs), `dcebb55b` (VPS docs), `b1d3b03d` (test control fix). Pushes ran the new bookkeeping pre-push (seconds).
- Async CI note: pushes touch `.githooks/**`, `scripts/lib/**`, `scripts/check-test-cleanliness.sh`, `scripts/clean-test-cleanliness` paths → `quality.yml` likely triggered (billable). Do not poll; if red, repair is next-session priority per constitution §10.

## Tracker state

- #330 closed with resolution. Open children: #331–#339 minus none claimed.
- Frontier now: **[Throughput: use warm targeted local validation instead of broad gates](https://github.com/jsongalvez/company_app/issues/331)** (unblocked by #330 closure; execution-order step 2) and [Throughput: use global skills only and remove repo-local skill sources](https://github.com/jsongalvez/company_app/issues/338) (unblocked since #310; any-time child). Prefer #331 (critical path).
- Remaining edges verified this session: #331←#330 ✓ (now satisfied), rest unchanged.

## Next action

1. Hydrate: read map #329 body (+ new comments) and #331 body + all comments before claiming.
2. `bash scripts/wayfinder-verify-child.sh 329 331`, claim exactly one frontier child (recommended: #331), resolve, write successor handoff.
3. Direct-to-master; smallest warm validation that answers the implementation question; no CI polling.
4. Environment unchanged: root `.env` present, Postgres up via docker compose, k6 CLI local, APP_PORT 8180 locally (8080 coolify proxy), ktlint 1.8.0 cached at `~/.cache/company-app/ktlint/`.

Stop. Session quota spent (one ticket resolved).
