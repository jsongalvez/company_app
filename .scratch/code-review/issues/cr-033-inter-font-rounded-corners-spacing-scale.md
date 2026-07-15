# CR-033: composeApp — Inter font not bundled + rounded corners/spacing scales

**Source:** Chunk 5 (spec: "rounded corners scale", "spacing scale", "Inter font not bundled")

**What:**
1. **Inter font not bundled:** Per design spec, the app should use Inter font. The font files are not included in `composeApp/src/commonMain/composeResources/font/` — fallback to system default.
2. **Rounded corners scale:** UI components lack a consistent corner radius scale (e.g., `4.dp`, `8.dp`, `12.dp`, `16.dp`). Different screens use ad-hoc values.
3. **Spacing scale:** No consistent spacing scale (e.g., `4.dp`, `8.dp`, `16.dp`, `24.dp`, `32.dp`). Screens use ad-hoc padding/margin values.

**Files:**
- `composeApp/src/commonMain/composeResources/font/` — add Inter font files
- `composeApp/src/commonMain/kotlin/.../ui/theme/` — LinearTheme, shape/spacing tokens

**Fix:**
1. Add Inter font files (`.ttf` or `.otf`) to compose resources
2. Define `CornerRadius` object with scale constants
3. Define `Spacing` object with scale constants
4. Update LinearTheme to reference font family and shape/spacing tokens
5. Refactor screens to use constants instead of ad-hoc values

**Priority:** low
**Story alignment:** US-056+ (composeApp design system)
