# Phase 2 Engine Specifications

Annotated pseudocode and SQL fragments for the service layer. Not executable DDL — implement in the application.

---

## Engine 1: Commission Recalculation (Issues #8, #19)

**Goal:** Calculate and persist the commission pool split for a branch day, accounting for per-sale attendance windows and manual overrides.

**Precision note:** All division must use `NUMERIC` / `BigDecimal` arithmetic. Never use `float` or `double` for commission calculations. `commission_amount_at_time` is `NUMERIC(15,4)` — preserve that scale throughout.

### Trigger Events

- New `product_sale` inserted
- `attendance` clock-in or clock-out updated
- `commission_manual_inclusion` added or changed
- If `branch_day.status` is `PAST` or `REMITTED`: automatic triggering is **disabled**. Coordinator must manually trigger a re-run via a dedicated UI action.

### Algorithm

Runs inside a single transaction. Input: `branch_day_id`.

**1. Guard**

Confirm `branch_day.status = 'OPEN'`. If not, abort unless this is a Coordinator-initiated manual re-run.

**2. Fetch sales**

```sql
SELECT id, commission_amount_at_time, quantity, sold_at
FROM product_sale
WHERE branch_day_id = :branch_day_id
```

**3. Initialize accumulator**

```
Map<UUID, NUMERIC> user_totals = {}
```

**4. Process each sale**

**a. Find eligible users from attendance:**
```sql
SELECT a.user_id
FROM attendance a
WHERE a.branch_day_id = :branch_day_id
  AND a.clock_in  <= :sale.sold_at
  AND (a.clock_out IS NULL OR a.clock_out >= :sale.sold_at)
```

**b. Apply manual overrides for this sale:**
```sql
SELECT user_id, is_included
FROM commission_manual_inclusion
WHERE product_sale_id = :sale.id
```
- `is_included = true` → ADD `user_id` to eligible set
- `is_included = false` → REMOVE `user_id` from eligible set

**c.** If eligible set is empty, skip this sale.

**d. Calculate share:**
```
sale_commission = sale.commission_amount_at_time * sale.quantity
per_user_share  = sale_commission / COUNT(eligible users)   -- NUMERIC division
```

**e. Accumulate:**
```
FOR EACH user_id IN eligible set:
  user_totals[user_id] += per_user_share
```

**5. Persist**

Accumulate the full map in memory first, then flush as a single batch. No prior `DELETE` — avoids a read window where splits appear as zero.

```sql
-- Upsert present users
INSERT INTO commission_split (branch_day_id, user_id, amount)
VALUES (:branch_day_id, :user_id, :total_amount)
ON CONFLICT (branch_day_id, user_id)
DO UPDATE SET amount = EXCLUDED.amount;

-- Remove users who dropped out of eligibility entirely
DELETE FROM commission_split
WHERE branch_day_id = :branch_day_id
  AND user_id NOT IN (:user_totals.keys)
```

**6. Commit.**

---

## Engine 2: Medical Mission Delegate Hook (Issue #3)

**Goal:** Ensure `MANAGER` users assigned as medical mission delegates automatically receive the `EDIT_BRANCH_DATA` capability scoped to the mission branch.

**Context type decision (locked):** Always use `context_type = 'BRANCH'` with `context_id = branch.id`. The `MEDICAL_MISSION` enum value in `capability_context_type` is reserved for future differentiated logic. All current auth checks use the `BRANCH` path.

### Grant Flow (`assignDelegate`)

Input: `target_user_id`, `branch_id` (of the mission), `assigned_by_id`

**1.** Begin transaction.

**2.** Insert delegate record:
```sql
INSERT INTO medical_mission_delegate (target_user, assigned_by)
VALUES (:target_user_id, :assigned_by_id)
RETURNING id AS delegate_id
```

**3.** Lookup `capability_id`:
```sql
SELECT id FROM capability WHERE code = 'EDIT_BRANCH_DATA'
```

**4.** Insert capability grant:
```sql
INSERT INTO user_capability (
    user_id, capability_id, context_type, context_id,
    source_type, source_id, priority, valid_from, valid_to
) VALUES (
    :target_user_id,
    :capability_id,
    'BRANCH',
    :branch_id,
    'MEDICAL_MISSION_DELEGATE',
    :delegate_id,
    20,
    now(),
    NULL   -- permanent until revoked via removeDelegate
)
```

**5.** Commit.

### Revoke Flow (`removeDelegate`)

Input: `delegate_id`

**1.** Begin transaction.

**2.** Set `ended_at` on delegate:
```sql
UPDATE medical_mission_delegate
SET ended_at = now()
WHERE id = :delegate_id
```

**3.** Expire the linked capability:
```sql
UPDATE user_capability
SET valid_to = now()
WHERE source_type = 'MEDICAL_MISSION_DELEGATE'
  AND source_id   = :delegate_id
  AND valid_to IS NULL
```

**4.** Commit.

---

## Engine 3: Remittance Submission (Issues #20, #21)

**Goal:** Freeze financial state at submission and prevent double-submission via optimistic locking.

**Isolation:** Use `SERIALIZABLE` transaction isolation for this operation.

### Submission Flow

Input: `remittance_id`, `expected_version` (from client), `submitted_by_id`

**1.** Begin transaction (SERIALIZABLE).

**2.** Lock and check state:
```sql
SELECT id, status, version
FROM remittance
WHERE id = :remittance_id
FOR UPDATE
```
- If `status != 'DRAFT'` → abort. _"Remittance already submitted."_
- If `version != :expected_version` → abort. _"Concurrent edit detected. Reload and retry."_

**3.** Calculate gross income:
```sql
SELECT COALESCE(SUM(rl.amount), 0)
FROM remittance_line rl
WHERE rl.remittance_id = :remittance_id
  AND rl.type          = 'SESSION'
  AND rl.deleted_at    IS NULL
```

**4.** Calculate total compensation:
```sql
SELECT COALESCE(SUM(c.amount), 0)
FROM compensation c
WHERE c.paying_branch_day_id IN (
    SELECT branch_day_id
    FROM remittance_day_breakdown
    WHERE remittance_id = :remittance_id
)
```

**5.** Calculate total expenses:
```sql
SELECT COALESCE(SUM(e.amount), 0)
FROM expense e
WHERE e.branch_day_id IN (
    SELECT branch_day_id
    FROM remittance_day_breakdown
    WHERE remittance_id = :remittance_id
)
  AND e.deleted_at IS NULL
```

**6.** Derive net income:
```
net_income = gross_income - total_compensation - total_expenses
```

**7.** Insert snapshot (immutability trigger blocks any future `UPDATE` or `DELETE`):
```sql
INSERT INTO remittance_financial_snapshot (
    remittance_id, gross_income, total_compensation,
    total_expenses, net_income
) VALUES (
    :remittance_id, :gross_income, :total_compensation,
    :total_expenses, :net_income
)
```

**8.** Finalize remittance:
```sql
UPDATE remittance
SET status         = 'SUBMITTED',
    version        = version + 1,
    submitted_date = CURRENT_DATE  -- Asia/Manila date; caller must pass correct value
WHERE id = :remittance_id
```

**9.** Mark covered days as remitted:
```sql
UPDATE branch_day
SET status = 'REMITTED'
WHERE id IN (
    SELECT branch_day_id
    FROM remittance_day_breakdown
    WHERE remittance_id = :remittance_id
)
```

**10.** Commit.
