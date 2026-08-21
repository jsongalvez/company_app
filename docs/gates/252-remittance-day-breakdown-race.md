# Gates - 252: remittance day-breakdown race safety

- [x] G1: Concurrent distinct UUID writes for one remittance and Branch Day return one existing row
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceLineServicePostgresTest.add day breakdown concurrent distinct IDs return existing'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: UUID collision across remittances remains a domain conflict
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceLineServicePostgresTest.add day breakdown rejects client ID already used by another remittance'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Day-breakdown audit remains exactly once for concurrent insertion
  CHECK: grep -F 'assertEquals(1, remittanceDayBreakdownAuditCount())' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: assertEquals(1, remittanceDayBreakdownAuditCount())

- [x] G4: Same UUID on another Branch Day remains a conflict
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceLineServicePostgresTest.add day breakdown rejects same ID for another Branch Day'
  EXPECT: EXIT 0
  EVIDENCE: Calculating task graph as no cached configuration is available for tasks: :backend:test --tests com.companyb.companyapp.service.RemittanceLineServicePostgresTest.add day breakdown rejects same ID for 
