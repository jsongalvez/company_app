# Gates - #242: attendance clock-in idempotency ownership

- [x] G1: Foreign caller and Branch retries are rejected
  CHECK: grep -q "Attendance id already belongs to another clock-in request" backend/src/main/kotlin/com/companyb/companyapp/service/attendance/AttendanceService.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Regression tests cover ownership rejection and no duplicate side effects
  CHECK: grep -q "clockIn rejects same id from another caller without duplicate side effects" backend/src/test/kotlin/com/companyb/companyapp/service/AttendanceServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Attendance service tests pass
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.AttendanceServicePostgresTest
  EXPECT: MATCHES BUILD SUCCESSFUL
  EVIDENCE: Reusing configuration cache.
