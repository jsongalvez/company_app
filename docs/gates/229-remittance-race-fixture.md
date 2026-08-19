# Gates - 229: fail-closed remittance race fixture

- [x] G1: Setup validates every dependent fixture response
  CHECK: grep -Fq 'requireResponse' tests/k6/remittance-race-test.js && grep -Fq 'setup branch' tests/k6/remittance-race-test.js && grep -Fq 'setup clock-in' tests/k6/remittance-race-test.js && grep -Fq 'setup client' tests/k6/remittance-race-test.js && grep -Fq 'setup session' tests/k6/remittance-race-test.js && grep -Fq 'setup remittance' tests/k6/remittance-race-test.js
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Setup uses Manila calendar date and confirmed Remittance ID
  CHECK: grep -Fq 'MANILA_OFFSET_MS' tests/k6/remittance-race-test.js && grep -Fq 'remitRes.json("id")' tests/k6/remittance-race-test.js && grep -Fq 'Setup remittance returned unexpected id' tests/k6/remittance-race-test.js && grep -Fq 'Setup remittance did not return version' tests/k6/remittance-race-test.js
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: k6 script passes syntax inspection
  CHECK: k6 inspect tests/k6/remittance-race-test.js >/dev/null && printf 'K6_INSPECT_OK\n'
  EXPECT: K6_INSPECT_OK
  EVIDENCE: K6_INSPECT_OK

- [x] G4: Setup aborts on failed dependencies instead of measuring invalid submissions
  CHECK: grep -Fq 'throw new Error' tests/k6/remittance-race-test.js && ! grep -Fq 'check(remitRes' tests/k6/remittance-race-test.js
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G5: Remittance-race thresholds come from shared named profile
  CHECK: grep -Fq 'thresholdProfiles.remittanceRace' tests/k6/remittance-race-test.js && grep -Fq 'remittanceRace:' tests/k6/helpers.js && grep -Fq 'checks: ["rate==1"]' tests/k6/helpers.js
  EXPECT: EXIT 0
  EVIDENCE: exit 0
