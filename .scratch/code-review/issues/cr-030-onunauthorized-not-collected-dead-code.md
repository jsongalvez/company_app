# CR-030: composeApp — onUnauthorized SharedFlow never collected + dead code

**Source:** Chunk 4 (spec: "onUnauthorized SharedFlow never collected", smells: "dead code", "fragile prefix comparison", "@Suppress bypasses")

**What:**
1. **`onUnauthorized` SharedFlow never collected:** The `ApiClient` exposes an `onUnauthorized: SharedFlow<Unit>` for handling 401 globally. No consumer collects this flow — unauthorized responses trigger no login redirect/re-login flow.
2. **Dead code:** Unused composables, helper functions, or ViewModel methods left from refactors.
3. **Fragile prefix comparison:** `path.startsWith("/api/")` check is fragile — should use structured route matching or constant.
4. **`@Suppress` bypasses:** Multiple `@Suppress("LongMethod")`, `@Suppress("MagicNumber")` annotations bypass detekt warnings without addressing the root cause.

**Files:**
- `composeApp/src/commonMain/kotlin/.../network/ApiClient.kt`
- `composeApp/src/commonMain/kotlin/.../viewmodel/*.kt`
- `composeApp/src/commonMain/kotlin/.../ui/` — dead composables
- `backend/.../` — @Suppress usages

**Fix:**
1. Collect `onUnauthorized` in a root composable/navigation host — on emission, navigate to login and clear stored token
2. Remove dead code after verifying no references
3. Replace fragile string prefix with shared route constant
4. Address root causes: split long methods, extract magic numbers to constants — remove `@Suppress`

**Priority:** medium
**Story alignment:** cross-cutting — composeApp navigation + security

**Status:** ✅ done
