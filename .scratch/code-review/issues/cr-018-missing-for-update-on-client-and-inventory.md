# CR-018: Missing FOR UPDATE row-level locks on client + branch_inventory

**Source:** Chunks 2a (spec: "FOR UPDATE on client C2"), 2b (spec: "no FOR UPDATE on branch_inventory D2")

**What:**
- **C2 — Concurrent Session Guard:** `SessionService` should acquire `SELECT ... FOR UPDATE` on the client row before creating a session, to serialize concurrent session creation. Per `docs/architecture_implementation_plan.md` §C2: "Before inserting, the service acquires a row-level lock on the client: `SELECT id FROM client WHERE id = :clientId FOR UPDATE`." Currently uses only the unique index as backstop.
- **D2 — Stock Integrity:** `branch_inventory` should be locked with `FOR UPDATE` before checking `current_stock >= delta`. Per `docs/architecture_implementation_plan.md` §D2: "SELECT current_stock FROM branch_inventory WHERE branch_id = :branchId AND product_id = :productId FOR UPDATE." Currently may rely only on optimistic locking.

**Spec reference:** `docs/architecture_implementation_plan.md` §C2, §D2

**Files:**
- `backend/.../service/SessionService.kt` — session create method
- `backend/.../service/ProductSaleService.kt` — `sell()` method
- `backend/.../repository/InventoryRepository.kt` — stock update method

**Fix:**
1. Add `FOR UPDATE` on client select before session insert (Exposed equivalent: may need custom SQL or use `REPEATABLE_READ` isolation)
2. Add `FOR UPDATE` on branch_inventory select before stock check in `InventoryRepository.adjustStock()`
3. Verify both are inside the same `transaction {}` as the subsequent insert/update
4. Note: Exposed DSL doesn't have native `forUpdate()` — use `TransactionManager.current().exec("SELECT ... FOR UPDATE")` as a one-time exception to the "no raw SQL" rule, with a comment explaining why

**Priority:** high
**Story alignment:** US-012 (Session Create), US-027 (Product Sale)
