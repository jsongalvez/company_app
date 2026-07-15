# CR-010: Migration integrity — V1 edited post-deployment, V3/V4 merged into V1

**Source:** Critical A — found in chunks 2c, 3, 4, 5 (standards + spec)

**What:**
- `V1__full_schema.sql` has been edited after initial deployment — break Flyway checksums on any DB that already ran the original V1
- V3 migrations (3 files) were merged into V1 and deleted from disk:
  - `V3__add_branch_id_to_medical_mission_delegate.sql`
  - `V3__client_middle_name_trgm.sql`
  - `V3__create_notification_table.sql`
- `V4__enable_pg_trgm.sql` was merged into V1 and deleted
- The current migration directory has no V3 files, creating a gap: V1 → V2 → V4

**Spec reference:** `docs/design_specification.md` §9: "Never edit a committed migration file — always add a new version"

**Files:**
- `backend/src/main/resources/db/migration/V1__full_schema.sql` — edited post-deployment, needs revert to original
- Missing: `V3__add_branch_id_to_medical_mission_delegate.sql`
- Missing: `V3__client_middle_name_trgm.sql`
- Missing: `V3__create_notification_table.sql`
- Missing: `V4__enable_pg_trgm.sql`
- `backend/src/main/resources/db/migration/V4__add_app_user_created_at.sql` — should become V5

**Fix:**
1. Restore original `V1__full_schema.sql` from the commit before the merge
2. Create `V5__full_schema_changes.sql` with all changes made to V1 since initial deployment
3. Restore V3 migrations as new V6 through V8
4. Restore `V4__enable_pg_trgm.sql` as V9
5. Renumber `V4__add_app_user_created_at.sql` → `V10__add_app_user_created_at.sql`
6. Verify no data loss: the "merged into V1" changes must be expressed as ALTER/ADD in the new migrations, not DROP+RECREATE

**Priority:** critical
**Story alignment:** cross-cutting — blocks all subsequent DB changes
