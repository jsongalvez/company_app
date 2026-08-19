# Gates - 237: type audit action in shared wire contract

- [x] G1: Shared and backend code compile with typed audit action
  CHECK: ./gradlew :shared:compileKotlinJvm :backend:compileKotlin
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Shared enum serialization tests pass
  CHECK: ./gradlew :shared:jvmTest --tests '*WireEnumsSerializationTest*'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Compose audit action has no local enum or parser
  CHECK: ! grep -nE 'private enum class AuditAction|AuditAction\.from|action = "' composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/screen/AuditLogScreen.kt composeApp/src/commonTest/kotlin/com/companyb/companyapp/ui/screen/AuditLogAcknowledgeTest.kt composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/AuditLogViewModelTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0
