# Handoff - Wayfinder Map #139 (OpenAPI documentation), Session 92

## What this is

Session 92 continued claimed ticket #178, Build: Add generated OpenAPI documentation to backend. Ticket remains OPEN and unshippable: review still proves only route-key and owner binding, not annotation-to-selected-handler correspondence.

## Session outcome

- Map #139 remains OPEN. Ticket #178 remains OPEN and assigned to `jsongalvez`.
- Strengthened generated evidence with exact route keys and selected-handler source text.
- Removed global response-function first-match inference and moved response-shape probing toward typed, route-local mapper evidence.
- Added stale selected-handler-source verification.
- Backend tests passed in 5 seconds.
- Gates passed 5/5 before current parser edits; rerun gate checker after next fixes.
- No ticket close, resolution comment, commit, or push was made because P1/P2/P3 still report HARD findings.

## Review status

- P1 HARD: class-level annotation metadata is still associated by method/path and owner; swapping annotations within one route object remains undetectable. Exact annotation-to-handler contract is missing.
- P2 HARD: named-handler extraction uses first textual function match; selected-handler containment checks only whether extracted text appears somewhere in file. Must use unique declaration plus exact source range/hash verification.
- P2 HARD: `responseExtension` and response-shape fallback still need fail-closed behavior. Any zero or ambiguous response mapper must stop normalization rather than retain/generated-infer a schema.
- P2 SOFT: `serviceBehavior` scans every service source for same method name; scope to resolved service or omit ambiguous status evidence.
- P3 HARD: handler correspondence remains unproven. P3 confirmed repeated normalization, stale artifact rejection, route coverage, request/security metadata, representative response statuses, and secret checks.
- P4 result pending when session stopped; rerun fresh P1-P4 after fixes.

## Verification

- `./gradlew :backend:test --console=plain`: passed.
- `node --check scripts/normalize-openapi-spec.mjs`: passed before latest uncommitted edits; rerun.
- `bash -n scripts/verify-openapi-spec.sh`: passed before latest uncommitted edits; rerun.
- `git diff --check`: passed before latest uncommitted edits; rerun.
- `./scripts/verify-openapi-spec.sh`: passed before latest parser edits; current artifact is stale and must be regenerated with `./gradlew :backend:compileKotlin --console=plain` first.
- `node scripts/gate-check.mjs docs/gates/178-openapi-documentation.md`: passed 5/5 before latest parser edits; rerun.
- `k6`: unavailable; pre-push load gate may block.

## How to drive the next session

1. Read this handoff, `CONTEXT.md`, root `AGENTS.md`, `backend/AGENTS.md`, `shared/AGENTS.md`, `docs/architecture.md`, `backend/docs/javalin-framework.md`, `docs/agents/gates.md`, and `docs/agents/code-review-loop.md`.
2. Load `/wayfinder`; inspect Map #139 at low resolution. Continue claimed #178 only; one ticket per session. Do not close #178 until one full P1-P4 pass has zero HARD findings, then run P5.
3. Run `node scripts/gate-check.mjs docs/gates/178-openapi-documentation.md` before edits.
4. Implement exact annotation-to-handler correspondence. Preferred shape: explicit route registration contract with unique selected-handler declaration/range and annotation binding; fail closed for class-level ambiguity. Do not rely on owner, path key, substring containment, or first textual function match.
5. Make response inference fail closed: exact selected-handler/service return/mapper ownership, one unambiguous response contract, explicit array evidence; reject zero or multiple candidates and stale/generated content that lacks source proof.
6. Scope service error/status source to resolved service; omit ambiguous same-name matches.
7. Rerun fresh P1-P4 passes until one pass has zero HARD findings. Run P5 and its loop-back if needed.
8. Only after review exit: rerun quality gates, backend tests with sufficient timeout and clean test DB, commit, run pre-push, push to `origin/ralph/company-app-full-build`, add #178 resolution comment, close #178, and update Map #139 Decisions so far.
9. After #178 closes, create or select next AFK task for measuring and shortening pre-commit/pre-push hooks while preserving safety checks.
10. Finish by writing `docs/agents/wayfinder-196-handoff.md`; stop after writing it.

## Critical blockers

- Annotation-to-selected-handler correspondence remains unproven for class-level annotations and named handlers.
- Response inference still has broad/ambiguous fallback paths and must fail closed.
- Current changes are uncommitted. Preserve them, inspect carefully, and do not revert user/agent work.
- `k6` unavailable; record exact pre-push skip if push becomes possible.

## Suggested skills for next session

- `/wayfinder` - continue Map #139 ticket #178.
- `/implement` - complete build and gate workflow.
- `/code-review` - rerun required phased loop.
- `/writing-for-agents` - required before next handoff edit.
