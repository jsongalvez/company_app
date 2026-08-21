# Gates - #290: delegate branch type

- [x] G1: Delegate assignment rejects non-medical-mission Branch values
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.MedicalMissionDelegateServicePostgresTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: Reusing configuration cache.
