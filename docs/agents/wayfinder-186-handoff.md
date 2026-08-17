# Handoff - Wayfinder Map #139 (OpenAPI documentation), Session 83

## What this is

Session 83 continued claimed ticket #178, Build: Add generated OpenAPI documentation to backend. Partial implementation was committed and pushed, but ticket remains OPEN because required phased review still has HARD findings.

## Session outcome

- Map #139 remains OPEN. Ticket #178 remains OPEN and assigned.
- Commit `12c2502` is pushed to `origin/ralph/company-app-full-build`.
- Removed synthetic Java no-op route index `OpenApiRouteDocumentation.java`.
- Added production route-object `@OpenApi` metadata across backend route files.
- KAPT is the single annotation-processing path. Broad JAR duplicate exclusion is removed.
- KAPT-generated OpenAPI JSON is normalized in place under `backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json`.
- Normalization adds non-empty info, bearer scheme, required path parameters, query metadata for known route families, request-body presence, response presence, and unique operation IDs.
- Exact production `(method, path)` coverage and secret checks are enforced by `scripts/verify-openapi-spec.sh`.

## Review status

Required phased loop was started. P1/P3/P4 still report HARD findings:

- Annotations are attached to production route singleton classes, not individual HTTP handler functions. Reviewer considers this detached from handler behavior and vulnerable to drift.
- Most request bodies are generic `type: object`; only login/register use actual DTO schemas. Production DTO schemas remain missing.
- Most successful responses lack DTO content schemas.
- Error responses are not endpoint-accurate. Normalizer fabricates `200` when absent and does not model actual 400/401/403/404/409/422/429 outcomes per endpoint.
- Query metadata is only partially mapped. Many route-specific query parameters remain undocumented.
- KAPT emits approximately 100 validation warnings for path parameters because annotations do not declare them at processor level; post-processing adds them afterward.
- P1/P3/P4 all independently identify verification gaps: current verifier checks route set, security, response presence, and path params, but cannot prove DTO schemas, query completeness, endpoint response status accuracy, or annotation-to-handler linkage.
- KAPT still reports repeated operation IDs before normalization; normalizer makes final IDs unique, but source annotations should be split into one operation per method with unique IDs.

## Verification

- `node scripts/gate-check.mjs docs/gates/178-openapi-documentation.md` passed 5/5.
- `./scripts/verify-openapi-spec.sh` passed: `OPENAPI_ROUTE_COVERAGE_OK` and `OPENAPI_SECRET_SCAN_OK`.
- `./gradlew :backend:detekt :backend:ktlintCheck` passed.
- `./gradlew :backend:compileKotlin` passed with configuration cache.
- `./gradlew clean :backend:test` exceeded 120 seconds during first run; retry `./gradlew :backend:test` exceeded 600 seconds while compiling/running tests and did not produce a final verdict. Timed-out tests left test data; `bash scripts/clean-test-db.sh` restored cleanliness.
- `git diff --check` passed before commit.
- Pre-push passed test-data cleanliness and composeApp desktop/Android compilation. k6 baseline skipped because `k6` is unavailable.
- Push succeeded after cleaning test DB.

## How to drive the next session

1. Read this handoff, `CONTEXT.md`, root `AGENTS.md`, `backend/AGENTS.md`, `docs/architecture.md`, `backend/docs/javalin-framework.md`, `docs/agents/gates.md`, and `docs/agents/code-review-loop.md`.
2. Load `/wayfinder`; inspect Map #139 at low resolution. Continue claimed #178 only; one ticket per session. Do not close #178 until review reaches one full P1-P4 pass with zero HARD findings, then run P5.
3. Run `node scripts/gate-check.mjs docs/gates/178-openapi-documentation.md` before edits.
4. Replace class-level annotations with annotations on actual handler functions or a generated source-of-truth that is mechanically bound to each production handler registration. Split multi-method annotations so each operation has unique source operationId.
5. Add actual DTO request/response schemas and exact query/path parameter declarations. Derive status responses from each handler's real `HttpStatus` and mapped domain exceptions, not generic normalizer defaults.
6. Strengthen `scripts/verify-openapi-spec.sh` to fail on missing DTO schemas, missing query parameters, inaccurate status sets, unresolved refs, duplicate operation IDs, and annotation/registration mismatch.
7. Rerun full backend test with a sufficient timeout and clean test DB after any timeout. Run phased P1-P4 again until zero HARD, then P5 and loop back if P5 fixes code.
8. Commit, run pre-push, push, add the ticket resolution comment only after review exit, close #178, and update Map #139 Decisions so far only then.
9. After #178 closes, create or select next AFK task for measuring and shortening pre-commit/pre-push hooks. Preserve safety checks while parallelizing independent work and caching safe results.
10. Finish by writing `docs/agents/wayfinder-187-handoff.md`; it is next chain completion signal. Stop after writing it.

## Critical blockers

- Ticket #178 is not shippable: generated documentation still lacks endpoint-accurate DTO/query/error metadata and handler-level binding.
- Backend test suite did not complete within 10 minutes. Determine whether this is expected test duration or a regression before claiming ticket completion.
- k6 is unavailable; pre-push records this documented skip.

## Suggested skills for next session

- `/wayfinder` - continue Map #139 ticket #178.
- `/implement` - complete build and gate workflow.
- `/code-review` - rerun required phased loop.
- `/writing-for-agents` - required before next handoff edit.
