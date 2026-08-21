# Legacy Code Review Contract

This document preserves the former uniform phased review policy. It is not the
active contract. Active policy lives in root `AGENTS.md` and
`docs/agents/code-review-loop.md`.

## Code review — phased loop

Every `/implement` ticket gets a **phased review loop**. The loop runs until
one full pass reports zero HARD findings.

A **pass** is four phases, each a different review mode, run as parallel
`/code-review` sub-agents:

1. **P1 Spec conformance** — full delta vs the ticket line-by-line: missing,
   partial, scope-creep, and wrong-implementation findings.
2. **P2 Standards + constraints** — full delta vs documented standards and
   every constraint source consumed by the code.
3. **P3 Behavior trace** — end-to-end state machines on the composed tree:
   every user flow, error path, repeated attempt, and navigation back-stack.
4. **P4 Adversarial edges** — races, ordering, double-taps, stale state,
   empty states, dead branches, non-exhaustive `when`s, and unmapped slots.

The exit pass also runs **P5 — architecture residue** as parallel
architecture-depth and hygiene-sweep agents. P5 findings land in the ARCH
bucket. P5 fixes trigger one standard P1-P4 loop-back.

## Loop mechanics

- Batch fixes are committed per pass. Each pass diffs from the previous pass.
- HARD findings must be fixed.
- SOFT findings may be accepted only with a logged reason and two independent
  phase sightings.
- Exit requires one full pass with zero HARD findings.
- Agents re-derive behavior from the ticket; previous passes are not authority.

This contract remains available for deliberate high-risk work or comparison
when the active risk-based profile is insufficient.
