# Gates — 289: gate text expectations require successful checks

- [x] G1: gate harness rejects matching output from failed checks
  CHECK: bash tests/gates/run.sh
  EXPECT: all green — checker harness
  EVIDENCE: ok   pass: exit 0
