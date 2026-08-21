-- =============================================================================
-- V16__role_derived_global_capabilities.sql
-- #132: role→capability view derivation for GLOBAL capabilities (ADR-0023).
-- Depends on V15 (capability_source_type 'ROLE').
--
-- The dead-grant class: MANAGE_USERS, ASSIGN_COMPENSATION, ASSIGN_DELEGATE and
-- GLOBAL VIEW_BRANCH_DATA are granted nowhere in production — role_capability
-- seeds (V2) are documentation-only, runtime checks active_user_capabilities,
-- and nothing writes GLOBAL user_capability rows outside tests. Fix at the
-- root: active_user_capabilities becomes a UNION of (a) direct user_capability
-- rows (unchanged) and (b) role-derived grants (user_role → role_capability →
-- capability). Business logic still checks ONLY the view — the V2 rule
-- "never check user_role directly" is preserved; the view is the single place
-- that computes the union.
--
-- DERIVATION RULE (which codes, which roles, GLOBAL context only):
--   role_capability has no context column (V2 scope comments are
--   documentation), so BRANCH/BRANCH_DAY-scoped codes (EDIT_BRANCH_DATA,
--   EDIT_PAST_DAY, VOID_SESSION, SUBMIT_REMITTANCE, MANAGE_PRODUCTS,
--   BRANCH-scoped ASSIGN_COMPENSATION/VIEW_BRANCH_DATA) CANNOT derive — a role
--   cannot express which branch. They continue to flow through direct
--   user_capability inserts (relief, delegate, future grant UI).
--   Derived rows are GLOBAL-context only:
--   - MANAGE_USERS, ASSIGN_DELEGATE, ASSIGN_COMPENSATION → SUPERUSER/OWNER/MANAGER
--     (the roles V2 grants them to; management-class ops).
--     COORDINATOR's ASSIGN_COMPENSATION is "assigned branches only" per V2 and
--     is deliberately NOT derived — GLOBAL derivation would over-grant.
--   - VIEW_BRANCH_DATA → SUPERUSER/ACCOUNTANT only (SUPERUSER "full access";
--     ACCOUNTANT "read-only across all branches", V2:132-138 / #105 F1).
--     GLOBAL VIEW_BRANCH_DATA = all branches is the #131 window semantics.
--     Branch-scoped staff roles (COORDINATOR/PRACTITIONER/MANAGER/OWNER) keep
--     BRANCH-scoped VIEW_BRANCH_DATA via direct grants — never all-branches.
--
-- V2 HEADER AMENDMENT (ticket #132 scope item 4): V2's "documentation only"
-- header comment cannot be edited — Flyway checksum validation would reject
-- the applied migration (#121 precedent). This header + the view definition
-- below ARE the amended documentation.
--
-- ROLE SEED NOTE: no production app_user/user_role rows exist (pre-launch);
-- this migration ships the derivation mechanism only. user_role assignment
-- rides the user-creation path — the dev/k6 flow already assigns OWNER via
-- DevSeeder; the production user-create flow (which must assign a role) is the
-- tracked #106 fog. The seed is 3 SQL lines once a bootstrap account exists.
--
-- PRIORITY: derived rows carry priority 5 (GrantPriorities.ROLE_DERIVED —
-- above the raw column default 0 so a role-derived row never loses to an
-- unspecified default, below every explicit grant (relief 10 / delegate 20 /
-- direct 100) so an explicit grant always beats a role-derived row). Keep in
-- sync with backend/.../model/GrantPriorities.kt.
-- =============================================================================

CREATE OR REPLACE VIEW active_user_capabilities AS
SELECT DISTINCT ON (granted.user_id, granted.capability_id, granted.context_type, granted.context_id)
    granted.user_id,
    granted.capability_id,
    granted.context_type,
    granted.context_id,
    granted.priority,
    granted.source_type
FROM (
    -- (a) Direct grants — unchanged (Issue #2 fix: context_type included in
    -- DISTINCT ON to prevent cross-context collapse; Issue #5 fix: INACTIVE
    -- users excluded).
    SELECT
        uc.user_id,
        uc.capability_id,
        uc.context_type,
        uc.context_id,
        uc.priority,
        uc.source_type
    FROM user_capability uc
    JOIN app_user au ON uc.user_id = au.id
    WHERE au.status = 'ACTIVE'
      AND now() BETWEEN uc.valid_from AND COALESCE(uc.valid_to, 'infinity'::timestamptz)

    UNION ALL

    -- (b) Role-derived grants — GLOBAL context only, derivation rule above.
    -- INACTIVE users excluded by the same status filter as branch (a);
    -- user_role has no valid window, so status is the only revocation.
    SELECT
        ur.user_id,
        rc.capability_id,
        'GLOBAL'::capability_context_type AS context_type,
        '00000000-0000-0000-0000-000000000000'::uuid AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_role ur
    JOIN app_user au ON au.id = ur.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN role_capability rc ON rc.role_id = r.id
    JOIN capability c ON c.id = rc.capability_id
    WHERE au.status = 'ACTIVE'
      AND (
          (
              c.code IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'ASSIGN_COMPENSATION')
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER')
          )
          OR
          (
              c.code = 'VIEW_BRANCH_DATA'
              AND r.name IN ('SUPERUSER', 'ACCOUNTANT')
          )
      )
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;
