# Handoff - Map #180, Session 289

## Session outcome

- Loaded `docs/agents/wayfinder-288-handoff.md`, Map #180, `/wayfinder`, `/writing-for-agents`, `CONTEXT.md`, business requirements, architecture, engines, audit guidance, decision loop, issue tracker, code-review loop, backend/shared instructions, and applicable context pointers.
- Verified native child linkage with `scripts/wayfinder-verify-child.sh 180 232`, then claimed #232 before edits.
- Resolved and closed [Build: make commission triggers atomic](https://github.com/jsongalvez/company_app/issues/232).
- Wrapped product-sale, attendance clock-in/out, and manual commission inclusion flows in outer Exposed transactions so source writes, audits, commission recalculation, and split replacement commit or roll back together.
- Serialized recalculation with a materialized `branch_day FOR UPDATE` lock. Split replacement now upserts and updates before deleting stale rows, preventing transient empty split output.
- Preserved PAST/REMITTED automatic-recalculation rules, authorization, inventory/session validation, and audit callbacks. Same-ID product-sale and clock-in retries now return existing state; concurrent product-sale/manual-inclusion conflicts converge.
- Added rollback/failure-injection, idempotency, and concurrent repeated-recalculation coverage.
- Map #180 Decisions so far now contains the #232 pointer. R15 remains in `Not yet specified`.

## Verification

- Gate ledger `docs/gates/232-commission-triggers-atomic.md`: 3/3 passed.
- Targeted commission, product-sale, and attendance tests: PASS.
- Full `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm :shared:jvmTest`: PASS.
- Pre-commit: formatting, backend quality, OpenAPI, cleanliness, shared compilation, and Postgres connectivity: PASS.
- Pre-push: OpenAPI, Compose Android/Desktop compilation, backend startup/health, k6 baseline with 0% errors, and final test DB cleanup: PASS.
- Review packet: structured mode, GPT-5.6 Luna, blind P1/P2 and P3/P4 positions; final P1/P2/P3/P4 zero HARD findings; P2/P4 derived-split audit SOFT accepted with logged reason; deterministic gate PASS; confidence high; artifacts: issue #232 resolution, gate ledger, commit `d952dc3`, and this handoff.
- Commit `d952dc3` pushed to `origin/ralph/company-app-full-build`.

## Next-session instructions

1. Load this handoff, Map #180, `/wayfinder`, `/codebase-design`, `/writing-for-agents`, and every applicable Context Pointer.
2. Query native child state and treat Map #180 as workflow authority.
3. Frontier is open and unblocked: claim exactly one child, with #233 [Build: remove automatic Flyway repair](https://github.com/jsongalvez/company_app/issues/233) next in native Map order.
4. Verify #233 parent linkage before claiming. Keep R15 in `Not yet specified` until deployment topology or overlapping scheduler invocation requirements become concrete.
