# Map #180 Candidate: Make Gate Text Expectations Require Successful Checks

## Question

Should `scripts/gate-check.mjs` reject plain-text `EXPECT` matches when the
checked command exits nonzero?

## Scope

Require successful command status for plain-text expectations, add a failing
command fixture whose output matches the expected text, and preserve explicit
`EXIT N` expectations. No gate format redesign.

## Evidence

- `scripts/gate-check.mjs:36-43` ignores `code` for plain-text expectations.
- `docs/agents/gates.md:8-11,46-47` treats matched output as gate acceptance.
- `tests/gates/run.sh:24-28,48-63` has no nonzero matching-output regression fixture.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: DELTA
- L1 fact integrity: pass
- L2 domain coherence: pass with HARD gate-integrity breach
- L3 long-term architecture: pass; checker owns expectation semantics
- L4 adversarial falsification: pass; failing command plus matching text can flip gate
- L5 comprehension: pass
- deterministic gate: fail; no test rejects nonzero matching-output commands
- HARD findings: zero after status guard and regression fixture
- SOFT findings: zero
- confidence: high
- artifact: Session 344 tooling audit; `gate-check.mjs:36-43`
