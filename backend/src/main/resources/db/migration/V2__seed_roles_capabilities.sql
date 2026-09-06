-- =============================================================================
-- V2__seed_roles_capabilities.sql
-- Seed: roles, capability codes, and role_capability assignments. Holds ALL
-- seeds (squashed #461): base V2 bundle + V5 scheduler capability +
-- V22 session-base-rate backfill data + V26 catalog capability.
-- Fresh databases migrate from V1+V2 alone (#548: V1 now also folds the retired
-- V3–V5 structure; V6 was a data-only backfill with no surviving structure); the
-- folded files (V5, V20–V27, V3–V6) are deleted and must not be resurrected.
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


-- -----------------------------------------------------------------------------
-- V5 fold (#461): RECEIVE_NEXT_APPOINTMENT_ALERTS scheduler capability seed.
-- CR-012: coordinator-only notifications. SUPERUSER intentionally holds no row
-- (its V2 grant above already ran) — Coordinator-only by design.
-- -----------------------------------------------------------------------------

INSERT INTO capability (code) VALUES ('RECEIVE_NEXT_APPOINTMENT_ALERTS');  -- scope: BRANCH

-- Grant to COORDINATOR role only. MANAGER and OWNER do not receive this capability.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'COORDINATOR'
  AND c.code = 'RECEIVE_NEXT_APPOINTMENT_ALERTS';


-- -----------------------------------------------------------------------------
-- V22 fold (#461, data only — the effective_from DEFAULT now() lives in V1):
-- backfill the BR-documented default base rates for pre-existing branches (#418).
-- Fresh-DB no-op (no branches/users at seed time): the NOT EXISTS guard plus the
-- NULL-actor guard insert nothing, and the UPDATE corrects zero rows. Preserved
-- verbatim so the squashed chain documents the provisioning rule.
-- -----------------------------------------------------------------------------

UPDATE session_base_rate
SET rate = 0.00
WHERE session_type = 'MEDICAL_MISSION'
  AND effective_until > now()
  AND rate <> 0.00;

INSERT INTO session_base_rate (id, set_by, branch_id, session_type, rate, effective_from, effective_until)
SELECT gen_random_uuid(),
       actor.id,
       b.id,
       t.session_type::session_type,
       t.default_rate,
       now(),
       TIMESTAMPTZ '9999-12-31 23:59:59+00'
FROM branch b
CROSS JOIN (SELECT u.id FROM app_user u ORDER BY u.id LIMIT 1) AS actor
CROSS JOIN (VALUES
    ('REGULAR',          2500.00),
    ('SECOND_SESSION',   2000.00),
    ('SUBSEQUENT',       1500.00),
    ('PROVINCIAL_FIRST', 3500.00),
    ('MEDICAL_MISSION',     0.00)
) AS t(session_type, default_rate)
WHERE actor.id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM session_base_rate r
      WHERE r.branch_id = b.id
        AND r.session_type = t.session_type::session_type
        AND r.effective_until > now()
  );


-- -----------------------------------------------------------------------------
-- V26 fold (#461): MANAGE_CATALOG GLOBAL catalog authority (#436, Option 3).
-- -----------------------------------------------------------------------------

INSERT INTO capability (code) VALUES ('MANAGE_CATALOG');  -- scope: GLOBAL

-- Catalog authority for SUPERUSER (full access), OWNER, MANAGER
-- (Coordinator superset), and COORDINATOR.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name IN ('SUPERUSER', 'OWNER', 'MANAGER', 'COORDINATOR')
  AND c.code = 'MANAGE_CATALOG';
