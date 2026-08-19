# Gates - #240: attendance clock-out ownership

- [x] G1: Foreign caller cannot clock out attendance
  CHECK: grep -q "existing.userId != callerId" backend/src/main/kotlin/com/companyb/companyapp/service/attendance/AttendanceService.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Regression test covers foreign caller rejection
  CHECK: grep -q "clockOut rejects another user's attendance" backend/src/test/kotlin/com/companyb/companyapp/service/AttendanceServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Attendance service tests pass
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.AttendanceServicePostgresTest
  EXPECT: MATCHES BUILD SUCCESSFUL
  EVIDENCE: Reusing configuration cache.
