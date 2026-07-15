-- =============================================================================
-- V2__seed_roles_capabilities.sql
-- Seed: roles, capability codes, and role_capability assignments.
--
-- RULES (from architecture_implementation_plan.md A1, A2):
--   - Role names are constants. Never add a role at runtime.
--   - Capability codes are constants. Never insert dynamically.
--   - This file is the single source of truth for what each role can do.
--   - At runtime, check active_user_capabilities — never user_role directly.
--
-- CONTEXT NOTE (A3):
--   All Phase 2 capability grants use context_type = 'BRANCH'.
--   GLOBAL-scoped capabilities (MANAGE_USERS, ASSIGN_DELEGATE) are enforced
--   at the service layer. The scope comments below are documentation only.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- ROLES
-- -----------------------------------------------------------------------------

INSERT INTO role (name)
VALUES ('SUPERUSER'),
       ('OWNER'),
       ('MANAGER'),
       ('COORDINATOR'),
       ('PRACTITIONER'),
       ('ACCOUNTANT'),
       ('ONBOARDING');


-- -----------------------------------------------------------------------------
-- CAPABILITY CODES
-- The application references these by string code — see shared/domain/.
-- Expand this list as new features are built; never remove or rename existing codes.
-- -----------------------------------------------------------------------------

INSERT INTO capability (code)
VALUES ('VIEW_BRANCH_DATA'),    -- scope: BRANCH
       ('EDIT_BRANCH_DATA'),    -- scope: BRANCH / BRANCH_DAY
       ('EDIT_PAST_DAY'),       -- scope: BRANCH
       ('VOID_SESSION'),        -- scope: BRANCH
       ('SUBMIT_REMITTANCE'),   -- scope: BRANCH
       ('ASSIGN_COMPENSATION'), -- scope: BRANCH
       ('MANAGE_PRODUCTS'),     -- scope: BRANCH
       ('MANAGE_USERS'),        -- scope: GLOBAL
       ('ASSIGN_DELEGATE');     -- scope: GLOBAL


-- -----------------------------------------------------------------------------
-- ROLE → CAPABILITY ASSIGNMENTS
-- -----------------------------------------------------------------------------

-- SUPERUSER — full access
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'SUPERUSER';


-- OWNER
--   Does NOT hold EDIT_PAST_DAY — Coordinator is sole editor of PAST/REMITTED days. (A2)
--   Does NOT hold VOID_SESSION or SUBMIT_REMITTANCE. (BR)
--   MANAGE_PRODUCTS: home branch only — enforced at service layer.
--   EDIT_BRANCH_DATA: OPEN days only — enforced at service layer.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'OWNER'
  AND c.code IN (
                 'VIEW_BRANCH_DATA',
                 'EDIT_BRANCH_DATA',
                 'ASSIGN_COMPENSATION',
                 'MANAGE_PRODUCTS',
                 'MANAGE_USERS',
                 'ASSIGN_DELEGATE'
    );


-- MANAGER — superset of COORDINATOR (BR: "all coordinator permissions apply, plus...")
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'MANAGER'
  AND c.code IN (
                 'VIEW_BRANCH_DATA',
                 'EDIT_BRANCH_DATA',
                 'EDIT_PAST_DAY',
                 'VOID_SESSION',
                 'SUBMIT_REMITTANCE',
                 'ASSIGN_COMPENSATION',
                 'MANAGE_PRODUCTS',
                 'MANAGE_USERS',
                 'ASSIGN_DELEGATE'
    );


-- COORDINATOR — sole editor of PAST/REMITTED records for assigned branches
--   MANAGE_PRODUCTS: assigned branches only — enforced at service layer.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'COORDINATOR'
  AND c.code IN (
                 'VIEW_BRANCH_DATA',
                 'EDIT_BRANCH_DATA',
                 'EDIT_PAST_DAY',
                 'VOID_SESSION',
                 'SUBMIT_REMITTANCE',
                 'ASSIGN_COMPENSATION',
                 'MANAGE_PRODUCTS'
    );


-- PRACTITIONER
--   EDIT_BRANCH_DATA: OPEN days only; access scope enforced at service layer.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'PRACTITIONER'
  AND c.code IN (
                 'VIEW_BRANCH_DATA',
                 'EDIT_BRANCH_DATA'
    );


-- ACCOUNTANT — read-only across all branches
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'ACCOUNTANT'
  AND c.code = 'VIEW_BRANCH_DATA';


-- ONBOARDING — zero capabilities until assigned to a branch
