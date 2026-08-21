# Gates - 227: active assignment conflict safety

- [x] G1: Assignment race regression is present
  CHECK: grep -q 'concurrent active assignment creation returns one success and one conflict' backend/src/test/kotlin/com/companyb/companyapp/service/UserBranchAssignmentServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Repository classifies active-key insert collisions
  CHECK: grep -q 'ConflictException("User already has an active assignment at this branch")' backend/src/main/kotlin/com/companyb/companyapp/repository/UserBranchAssignmentRepository.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Active assignment uniqueness remains database-enforced
  CHECK: grep -q 'idx_one_active_assignment' backend/src/main/resources/db/migration/V1__full_schema.sql
  EXPECT: EXIT 0
  EVIDENCE: exit 0
