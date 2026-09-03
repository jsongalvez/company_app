-- =============================================================================
-- V26__derive_global_catalog_capability.sql
-- #436: shared product catalog management moves to a distinct GLOBAL capability.
--
-- PROBLEM: product/category collection routes gate GLOBAL MANAGE_PRODUCTS, but
-- V2 scopes MANAGE_PRODUCTS to BRANCH and no view leg derives it GLOBALly — so
-- no assigned Coordinator/Owner can reach catalog management, while the
-- unguarded detail routes admit any authenticated caller who knows a UUID
-- (#437 evidence). Overloading BRANCH MANAGE_PRODUCTS (branch inventory
-- restock/movements, #418 base rates) with catalog authority would couple
-- branch stock operations to the global product master switch.
--
-- CHANGE: introduce MANAGE_CATALOG as the GLOBAL-scoped catalog authority
-- (map #422 decision #436, Option 3):
--   - capability row + role_capability grants for SUPERUSER, OWNER, MANAGER,
--     COORDINATOR. MANAGER rides along because it is a documented superset of
--     Coordinator; PRACTITIONER/ACCOUNTANT/ONBOARDING hold no catalog power.
--   - leg (b) derives MANAGE_CATALOG GLOBALly for those four roles.
--   - leg (c) excludes MANAGE_CATALOG like the other management codes, so no
--     branch-qualified catalog grant ever derives; catalog writes stay global.
--
-- PRESERVED INVARIANTS:
--   - Business logic checks only this view; never user_role directly (V2 rule).
--   - Explicit grants outrank derived rows (priority 5 unchanged).
--   - INACTIVE exclusion and the time-window filter unchanged.
--   - BRANCH MANAGE_PRODUCTS derivation and its inventory/base-rate gates
--     untouched; GLOBAL MANAGE_PRODUCTS still derives for nobody.
--   - The #131 strictness (GLOBAL never satisfies BRANCH-context gates)
--     untouched.
--
-- Amends ADR-0023 (MANAGE_CATALOG joins the GLOBAL derivation whitelist)
-- — see the amendment section there.
-- =============================================================================

INSERT INTO capability (code) VALUES ('MANAGE_CATALOG');  -- scope: GLOBAL

-- Catalog authority for SUPERUSER (full access), OWNER, MANAGER
-- (Coordinator superset), and COORDINATOR.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR')
  AND c.code = 'MANAGE_CATALOG';

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

    -- (b) role-derived GLOBAL grants: management codes for
    --     SUPERUSER/OWNER/MANAGER, all-branches VIEW_BRANCH_DATA for
    --     SUPERUSER/OWNER/ACCOUNTANT, and catalog authority for
    --     SUPERUSER/OWNER/MANAGER/COORDINATOR (#436).
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
              AND r.name IN ('SUPERUSER', 'OWNER', 'ACCOUNTANT')
          )
          OR
          (
              c.code = 'MANAGE_CATALOG'
              AND r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR')
          )
      )

    UNION ALL

    -- (c) role-derived BRANCH grants (#417): every non-management code of each
    --     assigned role, at every branch holding an ACTIVE assignment.
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
      AND c.code NOT IN ('MANAGE_USERS', 'ASSIGN_DELEGATE', 'MANAGE_CATALOG')
) granted
ORDER BY granted.user_id, granted.capability_id, granted.context_type, granted.context_id, granted.priority DESC;
