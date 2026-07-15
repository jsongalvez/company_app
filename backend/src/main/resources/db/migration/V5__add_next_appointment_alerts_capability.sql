-- =============================================================================
-- V5__add_next_appointment_alerts_capability.sql
-- Add RECEIVE_NEXT_APPOINTMENT_ALERTS capability for coordinator-only notifications.
-- CR-012: Replaces role-based query in NextAppointmentScheduler with capability check.
-- Only COORDINATOR role should receive next-appointment alerts (not MANAGER).
-- =============================================================================

INSERT INTO capability (code) VALUES ('RECEIVE_NEXT_APPOINTMENT_ALERTS');  -- scope: BRANCH

-- Grant to COORDINATOR role only. MANAGER and OWNER do not receive this capability.
INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id
FROM role r,
     capability c
WHERE r.name = 'COORDINATOR'
  AND c.code = 'RECEIVE_NEXT_APPOINTMENT_ALERTS';
