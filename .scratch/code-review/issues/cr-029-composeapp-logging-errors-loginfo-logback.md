# CR-029: composeApp logging — errors logged as logInfo, logback regression, SLF4J per-call

**Source:** Chunk 4 (standards: "errors logged as logInfo", "logback regression", "SLF4J per-call", "tautological message")

**What:**
1. **Errors logged as `logInfo`:** Multiple catch blocks use `logInfo` instead of `logError` when handling exceptions. Violates `AGENTS.md` "logError must be used in every catch block with the exception as the third arg."
2. **Logback regression:** Logback configuration may have regressed (missing appenders, wrong levels).
3. **SLF4J per-call:** `LoggerFactory.getLogger()` called per-request or per-method instead of storing a class-level `val`.
4. **Tautological message:** Error messages like "Error: operation failed" (no context, no values).

**Files:**
- `composeApp/src/commonMain/kotlin/.../viewmodel/*.kt` — catch blocks
- `composeApp/src/desktopMain/kotlin/.../util/Log.kt` — SLF4J usage
- `composeApp/src/desktopMain/resources/logback.xml`

**Fix:**
1. Replace all `logInfo` in catch blocks with `logError(tag, message, exception)`
2. Review logback.xml — ensure appenders, rolling file, proper levels
3. Store logger as `private val logger = LoggerFactory.getLogger(ClassName::class.java)` at class level
4. Include contextual values in error messages (e.g., `"Failed to load branch $branchId"` not just `"Failed"`)

**Priority:** medium
**Story alignment:** cross-cutting — all composeApp code
