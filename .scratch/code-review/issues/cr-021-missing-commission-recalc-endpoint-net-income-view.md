# CR-021: Missing POST /commission/recalculate endpoint + missing `net_income` SQL view

**Source:** Chunks 2b (spec: "no POST /commission/recalculate"), 3 (spec: "net_income missing from SQL view", "netIncome column in Exposed not SQL view")

**What:**
1. **No manual recalc endpoint:** Per `docs/engine_specifications.md` Engine 1, PAST/REMITTED days block automatic recalculation but "Coordinator must manually trigger a re-run via a dedicated UI action." No `POST /api/commissions/recalculate` endpoint exists for this.
2. **net_income missing from SQL view:** The `monthly_remittance_summary` (or similar reporting view) does not include `net_income`. Handoff says "net_income missing from SQL view" and "netIncome column in Exposed not SQL view" — the Exposed model for a reporting query references a `netIncome` column that should come from a SQL view but is computed in Kotlin instead.

**Files:**
- Create: `backend/.../api/routes/CommissionRoutes.kt`
- `backend/.../service/CommissionEngineService.kt` — add public `manualRecalculate` method
- `backend/.../repository/` — reporting views

**Fix:**
1. Add `POST /api/branches/{branchId}/days/{branchDayId}/commissions/recalculate` endpoint, gated on `EDIT_PAST_DAY` capability
2. Add `manualRecalculate(branchDayId, requestedBy)` method that bypasses the OPEN guard
3. Audit the reporting SQL views — ensure `net_income` is exposed as a column and the Exposed model references the view, not Kotlin computation
4. Wire into routes + Main.kt

**Priority:** medium
**Story alignment:** US-024 (Commission Recalculation), US-034 (Reporting)
