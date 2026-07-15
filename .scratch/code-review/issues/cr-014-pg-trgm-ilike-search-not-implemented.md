# CR-014: No pg_trgm ILIKE search — ClientRepository uses plain ILIKE only

**Source:** Critical E — found in chunks 1, 2a (spec: "pg_trgm ILIKE", smell: "pg_trgm extension unused")

**What:**
- `ClientRepository.search()` uses plain PostgreSQL `ILIKE` via custom Exposed `ILikeOp` — no trigram indexing
- The `V4__enable_pg_trgm.sql` migration was merged into V1 and deleted, so the extension may not be enabled on existing DBs
- Plain ILIKE is slower for fuzzy/typo-tolerant search — pg_trgm + GIN index would handle "Jhn" → "John" matching
- Handoff also notes: `V4__enable_pg_trgm.sql` was deleted in V1 merge

**Spec reference:** `docs/design_specification.md` §14: "pg_trgm extension with ILIKE for fuzzy name matching"

**Files:**
- `backend/src/main/kotlin/com/companyb/companyapp/repository/ClientRepository.kt` — lines ~197-234 (`search` method, `ILikeOp`, `ilike()` helper)
- Migrations: restore `V4__enable_pg_trgm.sql` (see CR-010)

**Fix:**
1. Ensure `CREATE EXTENSION IF NOT EXISTS pg_trgm` runs (part of CR-010 migration restore)
2. Add GIN index on client name columns: `CREATE INDEX idx_client_name_trgm ON client USING gin ((first_name || ' ' || last_name) gin_trgm_ops)`
3. Update `ClientRepository.search()` to use `similarity()` or `%` trigram operator via Exposed custom operator
4. Keep the token-based ILIKE as fallback for exact-ish matches, add similarity ranking for fuzzy
5. Test typo tolerance: "Jhn" should find "John", "Mria" should find "Maria"

**Priority:** high
**Story alignment:** US-011 (Client Search)
