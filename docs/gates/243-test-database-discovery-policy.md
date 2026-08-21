# Gates — 243: test-database discovery policy

- [x] G1: Shared discovery helper owns seed exclusions and base-table filtering
  CHECK: grep -n "test_data_tables\|information_schema.tables\|flyway_schema_history" scripts/lib/common.sh
  EXPECT: MATCHES test_data_tables
  EVIDENCE: 41:# --- test_data_tables USER DATABASE ---

- [x] G2: Cleanliness and cleanup scripts consume shared discovery helper
  CHECK: grep -n "test_data_tables" scripts/check-test-cleanliness.sh scripts/clean-test-db.sh
  EXPECT: MATCHES test_data_tables
  EVIDENCE: scripts/check-test-cleanliness.sh:20:TABLES_OUTPUT=$(test_data_tables "$DB_USER" "$DB_NAME")

- [x] G3: Shell validation covers syntax and discovery policy fixtures
  CHECK: bash -n scripts/lib/common.sh scripts/check-test-cleanliness.sh scripts/clean-test-db.sh scripts/test-db-discovery-test.sh scripts/test-db-discovery-disposable-test.sh && bash scripts/test-db-discovery-test.sh
  EXPECT: EXIT 0
  EVIDENCE: test DB discovery tests passed

- [x] G4: Disposable PostgreSQL fixture proves discovery and cleanup share policy
  CHECK: bash scripts/test-db-discovery-disposable-test.sh
  EXPECT: MATCHES disposable test DB discovery passed
  EVIDENCE: disposable test DB discovery passed
