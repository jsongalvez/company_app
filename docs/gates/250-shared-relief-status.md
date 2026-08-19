# Gates - #250 shared relief-access status owner

- [x] G1: Backend has no duplicate ReliefStatus declaration or references
  CHECK: test ! -e backend/src/main/kotlin/com/companyb/companyapp/repository/model/ReliefStatus.kt && ! grep -R "repository.model.ReliefStatus\|\bReliefStatus\b" backend/src/main shared/src/commonMain --include='*.kt'
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Relief access persistence uses shared ReliefAccessStatus directly
  CHECK: grep -q 'import com.companyb.companyapp.domain.ReliefAccessStatus' backend/src/main/kotlin/com/companyb/companyapp/repository/model/ReliefAccess.kt && grep -q 'customEnumeration<ReliefAccessStatus>' backend/src/main/kotlin/com/companyb/companyapp/repository/model/ReliefAccess.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Shared and backend relief access tests compile and pass
  CHECK: ./gradlew :shared:compileKotlinJvm :backend:test --tests '*ReliefAccessServicePostgresTest' -x :backend:publishOpenApiSpec
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
