# CR-019: Session base rate — no exclusion constraint (C3) + PII and pricing bugs

**Source:** Chunk 2a (spec: "missing exclusion constraint no_rate_overlap", "base rate gap C3", "PII nullified with '' not NULL", "base price defaults to 0 silently")

**What:**
1. **Missing exclusion constraint** for `session_base_rate` per C3: `EXCLUDE USING gist (branch_id WITH =, session_type WITH =, tstzrange(effective_from, effective_until) WITH &&)`. Without this, overlapping base rates can be inserted.
2. **Base rate gap detection:** When updating rates, the service doesn't validate `new.effective_from = old.effective_until + 1 microsecond` — gaps between rates are silently allowed.
3. **PII nullification:** Client anonymization sets PII fields to `""` (empty string) instead of `NULL`. Should be NULL per business requirements.
4. **Base price defaults to 0:** When base price is missing, silently defaults to 0 instead of requiring explicit value or error.

**Files:**
- `V1__full_schema.sql` — needs `no_rate_overlap` exclusion constraint added (via V5 migration, see CR-010)
- `backend/.../service/SessionService.kt` — rate update logic, session create
- `backend/.../repository/ClientRepository.kt` — anonymize method

**Fix:**
1. Add exclusion constraint via new migration (see CR-010)
2. Add gap validation in rate update service: if a gap would form, reject with 400
3. Change empty string → NULL in client anonymization
4. Require explicit base price or throw if missing

**Priority:** medium
**Story alignment:** US-014 (Session Pricing), US-011 (Client Management)
