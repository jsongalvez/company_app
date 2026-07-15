# CR-023: !! inside runCatching + OffsetDateTime.now() without zone

**Source:** Chunk 2b (standards hard: "!! inside runCatching (AllowanceRoutes)", "OffsetDateTime.now() without zone")

**What:**
1. **`!!` inside `runCatching`:** `AllowanceRoutes` calls `UUID.fromString(context.queryParam("key")!!)` inside a `runCatching` block. Per `backend/AGENTS.md` "Query parameter extraction": never use `!!` inside `runCatching` — use explicit null-check + throw, so a missing param gives `BadRequestResponse` (400) not `NullPointerException` (500).
2. **`OffsetDateTime.now()` without zone:** Uses system default timezone instead of `ZoneOffset.UTC`. Should be either:
   - `OffsetDateTime.now(ZoneOffset.UTC)` (when explicit value needed, e.g. in `insertIgnore`)
   - `CurrentTimestampWithTimeZone` (inside Exposed `transaction {}` — the DB clock)

**Files:**
- `backend/.../api/routes/AllowanceRoutes.kt`
- Search: `\.now\(\)` without `ZoneOffset.UTC` in service/repository layer

**Fix:**
1. Replace `!!` + `runCatching` with explicit null checks and `runCatching` on the UUID parse only
2. Replace bare `OffsetDateTime.now()` with `OffsetDateTime.now(ZoneOffset.UTC)` or `CurrentTimestampWithTimeZone`

**Priority:** medium
**Story alignment:** US-025 (Allowances)

## Resolution

**Part 1 (`!!` inside `runCatching`):** Already compliant. All `runCatching` blocks across route files wrap pure conversion logic (UUID.fromString, enum valueOf, BigDecimal) on already-null-checked values. No `!!` inside any `runCatching` block.

**Part 2 (`OffsetDateTime.now()` without zone):** Fixed in `SessionService.kt:102` — changed `OffsetDateTime.now()` → `OffsetDateTime.now(ZoneOffset.UTC)`. `Instant.now()` in DenyList.kt and JwtService.kt did not need changes (Instant is always UTC).

**Status:** ✅ done
