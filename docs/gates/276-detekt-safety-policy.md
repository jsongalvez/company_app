# Gates - #276: Detekt safety policy

- [x] G1: Required pre-edit negative control records existing backend Detekt failure
  CHECK: test -s /tmp/opencode/276-backend-detekt.log && grep -q 'Analysis failed with 257 weighted issues' /tmp/opencode/276-backend-detekt.log
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Repository overlay remains byte-equivalent to canonical upstream policy
  CHECK: cmp -s config/detekt/detekt-anti-slop.yml /home/ubuntu/anti-slop-detekt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Detekt typed task is registered with the merged policy configuration
  CHECK: ./gradlew :backend:detektMain --dry-run --no-daemon | grep -q ':backend:detektMain'
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: Safety-policy source coverage is documented
  CHECK: test -s docs/agents/architecture-audit-180.md && grep -q 'Session 326 safety-policy verification' docs/agents/architecture-audit-180.md
  EXPECT: EXIT 0
  EVIDENCE: exit 0
