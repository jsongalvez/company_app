# CR-001: Missing runCatching on path params in ReliefAccessRoutes

**Source:** Standards review of US-001–010 (chunk 1)

**What:** `ReliefAccessRoutes` calls `UUID.fromString(context.pathParam("requestId"))` without a `runCatching {}` wrapper. An invalid/malformed UUID throws `IllegalArgumentException` (HTTP 500) instead of `BadRequestResponse` (HTTP 400). Existing codebase convention (per `backend/AGENTS.md` "Query parameter extraction") uses explicit `runCatching { UUID.fromString(...) }.getOrElse { throw BadRequestResponse(...) }`.

**Files:** `backend/src/main/kotlin/.../api/routes/ReliefAccessRoutes.kt` (lines ~697, ~720)

**Fix:** Wrap path param UUID extraction in runCatching + BadRequestResponse fallback.

**Priority:** medium
**Story alignment:** US-008, US-009

**Status:** ✅ done
