# CR-048 — Audit remaining `@Suppress("ThrowsCount")` in backend

**Source:** Code review of CR-030 — 48 `ThrowsCount` suppressions remain after LongMethod cleanup.

**What:**
Audit and reduce `@Suppress("ThrowsCount")` usage in backend services, routes, and repositories. 48 instances remain, most in guard-clause validation methods.

**Priority:** medium
**Story alignment:** cross-cutting — code quality

**Status:** ❌ wontfix — guard-clause validation is the right design; same rationale as `@Suppress("ReturnCount")` in `backend/AGENTS.md`. Extracting throw sites into helpers just shuffles the suppression tag around (LongMethod → TooManyFunctions anti-pattern). No real code quality improvement.
