# Gates - 293: own backend Hikari shutdown

- [x] G1: Database lifecycle exposes idempotent close and recreates datasource after close
  CHECK: grep -q "fun close()" backend/src/main/kotlin/com/companyb/companyapp/database/DatabaseConfig.kt && grep -q "dataSourceInstance = null" backend/src/main/kotlin/com/companyb/companyapp/database/DatabaseConfig.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Javalin server stop and startup failure close database resources
  CHECK: grep -q "DatabaseConfig.close()" backend/src/main/kotlin/com/companyb/companyapp/Main.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Database lifecycle tests cover idempotent close and restart ownership
  CHECK: grep -q "close is idempotent" backend/src/test/kotlin/com/companyb/companyapp/database/DatabaseConfigTest.kt && grep -q "recreates" backend/src/test/kotlin/com/companyb/companyapp/database/DatabaseConfigTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0
