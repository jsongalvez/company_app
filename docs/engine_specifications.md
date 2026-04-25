# Phase 2 Engine Specifications

## 1. Commission Recalculation Engine (Issues #8, #19)

**Goal:** Calculate and persist the commission pool split for a branch day, accounting for practitioner attendance windows and manual overrides.

### Algorithm (Per Branch Day)

1.  **Fetch Sales:** Retrieve all `product_sale` records for the target `branch_day_id`.
2.  **Clear Current Splits:** Delete existing rows in `commission_split` for this `branch_day_id` (only if the day is `OPEN`).
3.  **Process Each Sale:**
    *   **Determine Pool:** `sale_commission = product.commission_amount * product_sale.quantity`.
    *   **Find Eligible Users (Attendance):**
        *   Query `attendance` for `user_id` where `clock_in <= product_sale.sold_at` AND (`clock_out IS NULL` OR `clock_out >= product_sale.sold_at`).
    *   **Apply Manual Overrides:**
        *   Add `user_id` from `commission_manual_inclusion` where `product_sale_id` matches AND `is_included = true`.
        *   Remove `user_id` from `commission_manual_inclusion` where `product_sale_id` matches AND `is_included = false`.
    *   **Calculate Share:** `user_share = sale_commission / count(eligible_users)`.
    *   **Accumulate:** Add `user_share` to a local map `Map<UserId, TotalAmount>`.
4.  **Persist Results:** For each user in the map, `UPSERT` into `commission_split`.

### Triggering Events
*   New `product_sale` inserted.
*   `attendance` record updated (Clock-in/out).
*   `commission_manual_inclusion` added/removed.
*   *Note:* If `branch_day.status` is `PAST` or `REMITTED`, automatic triggering is disabled. Coordinators must trigger manual re-run.

---

## 2. Medical Mission Delegate Hook (Issue #3)

**Goal:** Ensure MANAGER users assigned to a medical mission receive the necessary operational capabilities automatically.

### Grant Flow (`assignDelegate`)

1.  **Begin Transaction.**
2.  **Insert Delegate Record:** `INSERT INTO medical_mission_delegate (target_user, assigned_by, ...)`.
3.  **Insert Capability:**
    *   `capability_id` = Lookup code `EDIT_BRANCH_DATA`.
    *   `context_type` = `BRANCH` (or `MEDICAL_MISSION` if specifically scoped).
    *   `context_id` = The `branch_id` of the mission.
    *   `source_type` = `MEDICAL_MISSION_DELEGATE`.
    *   `source_id` = ID from step 2.
    *   `priority` = `20`.
4.  **Commit Transaction.**

### Revoke Flow (`removeDelegate`)

1.  **Begin Transaction.**
2.  **Update Delegate Record:** Set `ended_at = now()`.
3.  **Expire Capability:** Set `valid_to = now()` for `user_capability` where `source_id` matches.
4.  **Commit Transaction.**

---

## 3. Remittance Snapshot Hook (Issue #20, #21)

**Goal:** Freeze financial state and prevent double-submission.

### Submission Logic

1.  **Begin Transaction (Serializable isolation recommended).**
2.  **Check Status:** `SELECT status, version FROM remittance WHERE id = ?`. If `status != 'DRAFT'`, abort.
3.  **Verify Version:** If provided `version` != current `version`, abort (Optimistic Lock).
4.  **Calculate Totals:**
    *   `gross = SUM(rl.amount) FROM remittance_line WHERE remittance_id = ? AND type = 'SESSION'`.
    *   `compensation = SUM(c.amount) ...`
    *   `expenses = SUM(e.amount) ...`
5.  **Insert Snapshot:** `INSERT INTO remittance_financial_snapshot` with calculated values.
6.  **Finalize Remittance:**
    *   Set `status = 'SUBMITTED'`.
    *   Increment `version`.
7.  **Update Days:** Set `branch_day.status = 'REMITTED'` for all days linked via `remittance_day_breakdown`.
8.  **Commit Transaction.**
