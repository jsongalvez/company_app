# Gates — #263: commit message issue references

- [x] G1: commit-msg hook enforces references and merge exemption
  CHECK: bash scripts/test-commit-msg-hook.sh
  EXPECT: MATCHES PASS: commit-msg hook fixtures
  EVIDENCE: PASS: commit-msg hook fixtures

- [x] G2: hook and setup scripts pass shell syntax checks
  CHECK: bash -n .githooks/commit-msg scripts/setup-hooks.sh scripts/test-commit-msg-hook.sh
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: documented setup and policy are present
  CHECK: grep -F 'ref #<number>' AGENTS.md docs/agents/issue-tracker.md
  EXPECT: MATCHES ref #<number>
  EVIDENCE: AGENTS.md:Future non-merge commits must include `ref #<number>` somewhere in the commit
