# CR-034: MeService duplicate capability query + ApiClient config branches + speculative scope param

**Source:** Chunk 5 (smells: "MeService duplicates capability query", "ApiClient config branches", "speculative scope param", "duplicated test teardown")

**What:**
1. **MeService duplicates capability query:** Already captured in CR-032 — reusing that ticket.
2. **ApiClient config branches:** The `ApiClient` has platform-specific configuration branches (Android vs Desktop vs iOS) that could be unified with `expect`/`actual`.
3. **Speculative scope param:** A method parameter was added "just in case" for future use — dead parameter.
4. **Duplicated test teardown:** Already captured in CR-026 — reusing that ticket.

**Files:**
- `composeApp/src/commonMain/kotlin/.../network/ApiClient.kt`
- Various service method signatures — search for unused parameters

**Fix:**
1. (CR-032 handles MeService)
2. Use `expect`/`actual` for platform-specific API client configuration instead of `when (platform)`
3. Remove speculative/unused parameters
4. (CR-026 handles test teardown)

**Priority:** low
**Story alignment:** chunk 5 polish
