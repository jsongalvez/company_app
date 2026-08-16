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
   the checker — it must come back **fail-red** on the gates that need the fix (their
   `EVIDENCE` stays `pending`). That red run is the **negative-control record**: the target
   test demonstrably fails without the implementation, and the first run's output is the
   proof. Keep that output in the ticket's resolution comment.
2. Implement.
3. **Before claiming done**: re-run the checker — every box checked with evidence, then the
   phased review loop (`docs/agents/code-review-loop.md`).

The vacuous-test watch-lens (a test that would still pass with the fix stripped) stays a
**loop lens** — phases re-derive it on the composed tree — never a gate command. A
state-mutating "strip the fix" command (git stash chains) is out of scope for the checker:
it cannot be re-run safely and its verdict depends on the fix's VCS state, not the code.

The skip rule applies at step 1 — no gates file, the sequence is just "implement".

## Format

```
- [ ] G<n>: <acceptance criterion — checkable, exhaustive>
  CHECK: <shell command, run via sh -c from the repo root>
  EXPECT: <EXIT N | MATCHES <regex> | plain substring of stdout+stderr>
  EVIDENCE: <written by the checker on a verified run>
```

- `CHECK` runs from the **repo root** by construction — the checker resolves it from its own
  path, so invocation CWD never changes a verdict. `CHECK` commands should not background
  processes: a child that holds stdout pins the run, and one that blocks past the 60s
  timeout leaves an orphan.
- `EXPECT` defaults to `EXIT 0` when the line is *omitted*. A present-but-blank `EXPECT`
  (bare `EXPECT:` or whitespace only) is unmet (malformed, not a wildcard); an
  `EXIT`-prefixed non-numeric value is unmet too.
- `MATCHES` is a regex over stdout+stderr (multiline), evaluated in a **time-boxed child
  (5s)** — a bad regex or a backtracking-heavy pattern fails the gate closed, it never hangs
  the pass. An `^$` pattern matches only when both streams are truly empty.
- The checker truncates evidence to the first non-empty output line (200 chars), or records
  `exit <code>` when the command produces none.
- A gate without a `CHECK` is unmet; the run reports it and exits 1.
- A malformed box line (a `- [` line that doesn't parse as a gate — keep these files free of
  stray list markers) is an **error**, never a silent skip — a file with one fails without
  flipping anything.
- Gate ids are `G1`, `G2`, … (numeric). Belt fields are contiguous under their gate: a blank
  line ends the gate's field block.
- The checker clears `EVIDENCE` to `pending` when a previously met gate regresses, and
  re-verifies a hand-checked box rather than trusting it.

## Checker

```bash
node scripts/gate-check.mjs docs/gates/<file>.md     # run + write (atomic)
node scripts/gate-check.mjs --dry docs/gates/*.md    # verdicts only, never writes
```

- One writer per review pass: the **driving agent** runs the checker on the ticket's gates
  file; phases read the verdict. A phase that wants its own verdict uses `--dry`.
- Exit codes: 0 = every gate met, 1 = any gate unmet / file error, 2 = usage error.
- Writes are atomic (temp + rename) — concurrent runs cannot tear the file.
- Zero-dep Node 16+, 60s timeout per check. The harness (`tests/gates/run.sh`) guards the
  checker itself.

## How the loop consumes it

- **P1 (spec conformance)**: the driving agent's checker verdict is the mechanical half of
  the phase — every box met with fresh evidence. The lens then spends its budget on
  partial/scope-creep/wrong-implementation judgment.
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
```