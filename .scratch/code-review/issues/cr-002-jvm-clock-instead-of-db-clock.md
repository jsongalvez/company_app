# CR-002: JVM clock instead of CurrentTimestampWithTimeZone

**Source:** Standards review of US-001–010 (chunk 1)

**What:** Several repositories and services use `OffsetDateTime.now()` (JVM clock) inside transaction blocks instead of `CurrentTimestampWithTimeZone` (DB server clock). Per `backend/AGENTS.md` "Timestamp consistency": "Always use `org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone` (the DB server's clock) when writing timestamp values inside `transaction {}` blocks. Never use `java.time.OffsetDateTime.now()`."

**Files:**
- `ReliefAccessRepository.grantWithCapability` (~line 1723)
- `MedicalMissionDelegateRepository.revokeWithCapability` (~lines 1636, 1643)
- `AttendanceService.clockIn` (~line 2618) — passes JVM time into repo
- `AttendanceService.clockOut` (~line 2577) — passes JVM time into repo
- `AttendanceRepository.clockIn` (~line 1234) — explicit JVM clock in insertIgnore block

**Fix:** Replace `OffsetDateTime.now()` with `CurrentTimestampWithTimeZone` inside `transaction {}` blocks. For `insertIgnore` blocks (where defaultExpression is suppressed), use `OffsetDateTime.now(ZoneOffset.UTC)` with a comment explaining why.

**Priority:** high
**Story alignment:** US-006, US-007, US-008, US-009, US-010

**Status:** ✅ done
