# Gates — #312: enforceable Wayfinder AFK sessions

- [ ] G1: Wayfinder branch and CI state tests pass
  CHECK: bash scripts/wayfinder-afk-test.sh
  EXPECT: EXIT 0
  EVIDENCE: pending

- [ ] G2: Wayfinder loop shell syntax is valid
  CHECK: bash -n scripts/wayfinder-loop.sh scripts/wayfinder-ci.sh
  EXPECT: EXIT 0
  EVIDENCE: pending
