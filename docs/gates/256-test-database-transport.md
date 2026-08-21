# Gates — 256: test-database transport

- [x] G1: Cleanliness and cleanup use shared transport rather than hard-coded container access
  CHECK: ! grep -n "docker exec company-postgres psql" scripts/check-test-cleanliness.sh scripts/clean-test-db.sh
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Both operations call host/container-aware shared transport
  CHECK: grep -n "test_db_psql" scripts/check-test-cleanliness.sh scripts/clean-test-db.sh
  EXPECT: MATCHES test_db_psql
  EVIDENCE: scripts/check-test-cleanliness.sh:39:COUNTS=$(test_db_psql \

- [x] G3: Shell syntax and existing discovery fixture pass
  CHECK: bash -n scripts/lib/common.sh scripts/check-test-cleanliness.sh scripts/clean-test-db.sh scripts/test-db-discovery-test.sh && bash scripts/test-db-discovery-test.sh
  EXPECT: EXIT 0
  EVIDENCE: test DB discovery tests passed

- [x] G4: Disposable database cleanup and cleanliness pass
  CHECK: bash scripts/test-db-discovery-disposable-test.sh
  EXPECT: MATCHES disposable test DB discovery passed
  EVIDENCE: disposable test DB discovery passed
