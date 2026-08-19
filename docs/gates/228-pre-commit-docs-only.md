# Gates - 228: pre-commit docs-only gate skipping

- [x] G1: Pre-commit classifies staged files before running quality gates
  CHECK: grep -Fq 'STAGED_FILES="$(git diff --cached --no-renames --name-only)"' .githooks/pre-commit && grep -Fq 'classify-push-files.sh' .githooks/pre-commit
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G5: Docs-only pre-commit execution exits before expensive commands
  CHECK: bash scripts/pre-commit-docs-only-test.sh
  EXPECT: EXIT 0
  EVIDENCE: pre-commit docs-only fixture: PASS

- [x] G2: Existing classifier fixtures cover docs-only and fail-closed cases
  CHECK: bash scripts/classify-push-files-test.sh
  EXPECT: EXIT 0
  EVIDENCE: push classification fixtures: PASS

- [x] G3: Pre-commit syntax remains valid
  CHECK: bash -n .githooks/pre-commit scripts/classify-push-files.sh scripts/classify-push-files-test.sh scripts/pre-commit-docs-only-test.sh
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: Docs-only branch skips all expensive gate commands
  CHECK: grep -Fq 'Documentation-only staged change' .githooks/pre-commit && grep -Fq 'exit 0' .githooks/pre-commit
  EXPECT: EXIT 0
  EVIDENCE: exit 0
