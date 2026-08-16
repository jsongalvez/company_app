# Gate ledger — runnable acceptance for builds

A **gates file** is a build ticket's acceptance criteria written as `CHECK`/`EXPECT`/`EVIDENCE`
triplets before the build starts. The checker (`scripts/gate-check.mjs`) runs each `CHECK` from
the **repo root**, flips the box and writes the first non-empty output line as `EVIDENCE` only
when `EXPECT` matches.

One sentence: **a checkbox is a claim; `EVIDENCE` is the proof.** The checker is the only
writer of both — it re-runs every gate every time, so a checked box carries a fresh
verification, never someone's earlier claim. This is the truth-class register (claims vs
shipped state) made executable.

## When to write

- **Every `/implement` build** (the #175-successor class).
- **Skip** decision/research tickets and sub-30-minute fixes — the ledger earns its cost on
  builds, not on small work.

Gates files live at `docs/gates/<issue>-<slug>.md` — repo-tracked so handoffs and review
passes can reference them.

## The implement sequence

1. **Before any code**: write the gates file from the ticket's resolution comment, then run
   the checker — expecting fail-red or already-met (a freshly written gates file against an
   unimplemented ticket should be red where the fix isn't in).
2. Implement.
3. **Before claiming done**: re-run the checker — every box checked with evidence, the
   negative-control gate included, then the phased review loop (`docs/agents/code-review-loop.md`).

The skip rule above applies at step 1 — no gates file, the sequence is just "implement".

## Format

```
- [ ] G<n>: <acceptance criterion — checkable, exhaustive>
  CHECK: <shell command, run via sh -c from the repo root>
  EXPECT: <EXIT N | MATCHES <regex> | plain substring of stdout+stderr>
  EVIDENCE: <written by the checker on a verified run>
```

Gate ids are `G<n>` (or `G-nc` for the negative-control gate, section below).

- `CHECK` runs from the **repo root** by construction — the checker resolves it from its own
  path, so invocation CWD never changes a verdict.
- `EXPECT` defaults to `EXIT 0` when the line is omitted. A present-but-blank `EXPECT` is
  unmet (malformed, not a wildcard).
- `MATCHES` is a regex over stdout+stderr (multiline); a bad regex fails the gate closed.
- The checker truncates evidence to the first non-empty output line (200 chars).
- A gate without a `CHECK` is unmet; the run reports it and exits 1.
- A malformed box line (a `- [` line that doesn't parse as a gate) is an **error**, never a
  silent skip — a file with one fails without flipping anything.
- The checker clears `EVIDENCE` to `pending` when a previously met gate regresses, and
  re-verifies a hand-checked box rather than trusting it.

## Negative-control gate — default on builds

Every build carries a gate that proves the fix is load-bearing, guarding the **vacuous-test**
watch-lens (handoffs' watch-lists live in the #178/#179 pattern; the lens name is
"Useless-test pruning" in P5a):

```
- [ ] G-nc: <test> fails red with the fix stripped
  CHECK: git stash push -q -- <paths to the fix>; ./gradlew <target> --tests "<test>"; rc=$?; git stash pop -q; if [ "$rc" -ne 0 ]; then echo "EXPECTED RED"; exit 0; else echo "test did NOT fail red with the fix stripped"; exit 1; fi
  EXPECT: EXPECTED RED
  EVIDENCE: pending
```

Shape rules — all three are load-bearing:

- The restore runs **unconditionally** (`;` chains, never `&&`): the test failing red (the
  gate's whole purpose) must not abandon the stash.
- The verdict is a **marker**, not the last command's exit code — `git stash pop` rebases the
  shell's exit status; decide on the echoed marker.
- `EXPECTED RED` (exit 0) proves the red state; the vacuous outcome is a nonzero exit with
  its own message, so a fix-less test fails the gate.

## Checker

```bash
node scripts/gate-check.mjs docs/gates/<file>.md     # run + write
node scripts/gate-check.mjs --dry docs/gates/*.md    # verdicts only
```

- Flips `- [ ]` → `- [x]` and writes fresh `EVIDENCE` only when `EXPECT` matches; a stale or
  failing claim is unflipped or overwritten, never trusted.
- Exit codes: 0 = every gate met, 1 = any gate unmet / file error, 2 = usage error.
- The harness (`tests/gates/run.sh`, 36 assertions) guards the checker itself.

## How the loop consumes it

- **P1 (spec conformance)**: run the checker first — the mechanical half of the phase is
  "every box met with fresh evidence". The lens then spends its budget on what it judges:
  partial, scope-creep, wrong-implementation.
- **Every later pass**: re-run the checker on the current tree — the evidence is re-derived,
  never inherited (self-certification is worthless).
- **Handoffs**: build picks cite their gates file, so the next session's first act is
  `node scripts/gate-check.mjs <file>` — fail-red-or-met, no re-reading the spec.
- **Resolution comment**: carries decisions; the gates file carries acceptance.

## Template

```markdown
# Gates — <ticket>: <slug>

- [ ] G1: <primary acceptance>
  CHECK: <command>
  EXPECT: <pattern>
  EVIDENCE: pending
...
- [ ] G-nc: <test> fails red with the fix stripped
  CHECK: <negative-control command per the shape rules>
  EXPECT: EXPECTED RED
  EVIDENCE: pending
```