-- =============================================================================
-- V21__derive_branch_scoped_role_capabilities.sql
-- #417: staff-role branch-scoped capability provisioning.
--
-- PROBLEM: role + home-branch assignment conferred zero operational
-- BRANCH-scoped grants (only the single Coordinator alerts code derived), and
-- the V1 header sent every other BRANCH-scoped code "through direct inserts only" — but the
-- only production writers of user_capability rows are the relief (#357) and
-- medical-mission delegate (#374) grant paths. Every ops/finance surface gated
-- on a BRANCH-context code 403'd for freshly created staff (#344 create-user +
-- role replace, then a home-branch assignment).
--
-- CHANGE: leg (c) widens to each assigned role's branch-scoped bundle, keyed on
-- ACTIVE assignments. Pure-view, zero writes (ADR-0023's rejection of
-- materialization stands); assignment end/reassignment naturally scopes the
-- window (ended_at IS NULL filter unchanged).
--
-- DERIVATION RULE: derive every role_capability row whose code is not a
-- management code. MANAGE_USERS stays company-wide GLOBAL (map #384 ruling)
-- and ASSIGN_DELEGATE is GLOBAL-enforced everywhere, so neither ever derives
-- BRANCH-scoped. All other codes (VIEW_BRANCH_DATA, EDIT_BRANCH_DATA,
-- EDIT_PAST_DAY, VOID_SESSION, SUBMIT_REMITTANCE, ASSIGN_COMPENSATION,
-- MANAGE_PRODUCTS, RECEIVE_NEXT_APPOINTMENT_ALERTS) are branch-owned by their
-- V2 scope comments and now reach their holders through assignments alone.
-- COORDINATOR's ASSIGN_COMPENSATION lands BRANCH-scoped exactly as V2 always
-- documented ("assigned branches only"); OWNER/MANAGER additionally keep the
-- ADR-0023 GLOBAL derivation, which remains authoritative for the GLOBAL-gated
-- surfaces.
--
-- PRESERVED INVARIANTS:
--   - Business logic checks only this view; never user_role directly (V2 rule).
--   - Explicit grants outrank derived rows (priority 5 < relief 10 / delegate
--     20 / direct 100 — GrantPriorities.kt stays in sync).
--   - No GLOBAL over-grant: legs (a)/(b) byte-identical; INACTIVE exclusion
--     and the time-window filter unchanged; ONBOARDING (empty role bundle)
--     still derives nothing.
--   - Relief/delegate grants and the #131 strictness (GLOBAL never satisfies
--     BRANCH-context gates) untouched.
--
-- Amends ADR-0023 (branch-scoped derivation is no longer "deliberately not
-- bridged") — see the amendment section there.
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
    -- (a) direct grants — user_capability rows, unchanged semantics.
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

    -- (b) role-derived GLOBAL grants (ADR-0023 whitelist, unchanged):
    --     MANAGE_USERS / ASSIGN_DELEGATE / ASSIGN_COMPENSATION for
    --     SUPERUSER/OWNER/MANAGER; VIEW_BRANCH_DATA for SUPERUSER/ACCOUNTANT.
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

    UNION ALL

    -- (c) role-derived BRANCH grants (#417): every non-management code of each
    --     assigned role, at every branch holding an ACTIVE assignment. Role
    --     bundles come straight from role_capability — no per-role whitelist.
    --     RECEIVE_NEXT_APPOINTMENT_ALERTS reaches only Coordinators because only
    --     their role holds it (V5); ONBOARDING derives nothing because its bundle
    --     is empty.
    SELECT
        uba.user_id,
        rc.capability_id,
        'BRANCH'::capability_context_type AS context_type,
        uba.branch_id AS context_id,
        5::smallint AS priority,
        'ROLE'::capability_source_type AS source_type
    FROM user_branch_assignment uba
    JOIN app_user au ON au.id = uba.user_id
    JOIN user_role ur ON ur.user_id = uba.user_id
    JOIN role r ON r.id = ur.role_id
    JOIN role_capability rc ON rc.role_id = r.id
    JOIN capability c ON c.id = rc.capability_id
    WHERE au.status = 'ACTIVE'
      AND uba.ended_at IS NULL
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE')
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;
