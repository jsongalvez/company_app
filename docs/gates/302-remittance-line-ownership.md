# Gates - 302: remittance-line UUID request ownership

- [x] G1: Same-parent UUID retries reject altered immutable request fields
  CHECK: grep -q 'fun `add line rejects altered UUID retry payload`' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt && grep -q 'fun `add line rejects altered UUID retry source and creator`' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt && grep -q 'fun `add line concurrent same UUID returns one line and audit`' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Existing idempotent retry preserves version, amount, and audit count
  CHECK: grep -q 'fun `add line idempotent duplicate returns existing`' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt && grep -q 'assertEquals(auditCountAfterFirst, remittanceLineAuditCount())' backend/src/test/kotlin/com/companyb/companyapp/service/RemittanceLineServicePostgresTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Repository compares all immutable request fields before returning an existing row
  CHECK: grep -n -E 'assertRequestMatches|createdBy|amount|sessionId|productSaleId' backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceLineRepository.kt
  EXPECT: MATCHES assertRequestMatches
  EVIDENCE: 29:    val sessionId: UUID?,
