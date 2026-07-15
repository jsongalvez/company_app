# CR-031: Null safety and theme tokens — UUID.fromString(null) + LinearTheme gaps

**Source:** Chunk 5 (standards hard: "UUID.fromString(null) no null guard", "LinearTheme missing tokens")

**What:**
1. **`UUID.fromString(null)` without null guard:** At least one code path passes a nullable string to `UUID.fromString()` without a null check — will throw `NullPointerException` instead of `BadRequestResponse`.
2. **LinearTheme missing tokens:** The `LinearTheme` compose theme is missing design tokens (colors, typography, shapes) that were defined in the PRD.

**Files:**
- `composeApp/src/commonMain/kotlin/.../ui/LinearTheme.kt`
- `backend/.../` or `composeApp/.../` — UUID.fromString calls without null guard

**Fix:**
1. Add null guard before all `UUID.fromString()` calls: `param?.let { UUID.fromString(it) } ?: throw BadRequestResponse("...")`
2. Populate LinearTheme with missing tokens: rounded corners scale, spacing scale (per spec)
3. Search: `UUID.fromString(` across codebase — verify null-safe on every call

**Priority:** medium
**Story alignment:** US-055+ (composeApp rebuild)
