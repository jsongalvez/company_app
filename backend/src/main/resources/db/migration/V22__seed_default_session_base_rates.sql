-- =============================================================================
-- V22__seed_default_session_base_rates.sql
-- #418: backfill the BR-documented default base rates for existing branches.
--
-- PROBLEM: session create fails closed with "No base rate configured" when a
-- branch has no open rate row for the session's type, and nothing provisioned
-- rate rows outside tests — every pre-existing branch 400s on its first
-- session. Branch creation now seeds the defaults in the same command
-- transaction (BranchService.create); this migration covers branches that
-- already exist.
--
-- CHANGE: one open row per missing (branch, session_type) pair at the
-- documented default (docs/business-requirements.md "Base Rates";
-- MEDICAL_MISSION always ₱0). set_by attributes the system provisioning to the
-- lowest seeded user id (the branch table records no creator; every real
-- deployment has at least one user before any branch exists, and an empty
-- app_user makes the scalar subquery NULL → the whole insert no-ops).
--
-- The NOT EXISTS guard keeps the no_rate_overlap exclusion constraint satisfied
-- by construction: only types with no open window are inserted. An open
-- MEDICAL_MISSION row with a non-zero rate predating the #405 normalization is
-- corrected in place — the ₱0 invariant holds everywhere, including rows the
-- backfill did not create.
--
-- IDEMPOTENCE: migration runs once per database; reruns are impossible by
-- Flyway checksum. Re-creation of equal rows is prevented by the same guard.
-- =============================================================================

UPDATE session_base_rate
SET rate = 0.00
WHERE session_type = 'MEDICAL_MISSION'
  AND effective_until > now()
  AND rate <> 0.00;

-- effective_from gains the DB-clock default its DDL always implied (the #322
-- time-ownership seam: writers must not read the JVM wall clock). Exposed omits
-- unset columns from INSERTs, so the default is what provisions the window start.
ALTER TABLE session_base_rate ALTER COLUMN effective_from SET DEFAULT now();


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
