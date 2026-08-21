# Backend Detekt Fixture Gate

## Scope

Backend tests now use deterministic `TestFixtures.uuid`, `TestFixtures.today`, and
`TestFixtures.now` values. Tests that intentionally exercise wall-clock expiry use
the narrowly named `realNow` and `waitForNextSecond` helpers.

## Evidence

| Check | Result |
|---|---|
| Ambient UUID/date/time calls in `backend/src/test` | PASS: zero matches |
| Typed test compilation | PASS: `:backend:compileTestKotlin` |
| Backend tests | PASS: 956 tests |
| Test database cleanup | PASS: `scripts/clean-test-db.sh` |
| Test-source ktlint | PASS: `:backend:ktlintTestSourceSetCheck` |
| `git diff --check` | PASS |
| Typed test safety rules | PASS: no `ForbiddenMethodCall` or `ForbiddenSuppress` findings |

## Negative Control

Typed Detekt was run against the unmodified policy before fixture migration and
reported ambient UUID/date/time calls. After migration, those safety findings are
absent without changing `ForbiddenMethodCall` or adding a baseline.

## Remaining Rollout Work

Typed backend Detekt still reports existing complexity/style findings in both
production and test sources. No broad exclusion or baseline was added; those
findings remain for the ordered ratchet/closeout child.

## Review Packet

- Mode: structured
- Model: GPT-5.6 Luna
- Blind position: ALPHA
- L1 fact integrity: pass; source search and typed task output agree
- L2 domain coherence: pass; deterministic fixtures preserve test ownership and disposable DB behavior
- L3 long-term architecture: pass; one test-fixture owner, no production seam or policy duplication
- L4 adversarial falsification: pass; UUID uniqueness, expiry boundary, full tests, and cleanup verified
- L5 comprehension: pass; real-time exceptions are explicitly named and limited to boundary tests
- Deterministic gate: pass; zero ambient test calls and safety-rule absence verified
- HARD findings: zero
- SOFT findings: existing complexity/style backlog, deferred to rollout closeout
- Confidence: high
- Artifact: this ledger and `backend/src/test/kotlin/com/companyb/companyapp/test/TestFixtures.kt`
