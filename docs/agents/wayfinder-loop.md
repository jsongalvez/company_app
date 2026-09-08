# Wayfinding chain daemon

Unattended driver for the wayfinder handoff chain: a tmux session
(`wayfinder-loop`) running `tools/wayfinder/wayfinder-loop.sh`. It watches
`.wayfinder/handoffs/` for new `wayfinder-*-handoff.md` packets, spawns a
fresh zero-context opencode2 session to execute each one, supervises the
session (questions, permissions, stalls), and chains to the next packet.

Runtime files (gitignored): `.wayfinder-loop.state` (last doc, session id,
seen-doc fingerprints, retries, hosted-CI verdict/repair mappings),
`.wayfinder-loop.log`, and `.wayfinder-loop.lock`.

## Rules

- **Quiesce the daemon before touching sessions or the worktree.** The stop
  detector re-prompts any session that stops without a handoff — an operator
  killed duplicate looks like a stalled worker and gets resumed against you.
- **One packet, one filename, forever — but content is the signal.** A handoff
  endpoint is any content change under `.wayfinder/handoffs/`: a NEW successor
  file, or an in-place revision of the active packet (revision chains as the
  successor link on session exit). Editing a queued packet before spawn just
  re-baselines it (#336). The daemon spawns only after the supervised session
  exits, so a mid-flight edit can never double-spawn.
- **End every chain session with a clean worktree and a recorded endpoint.**
  Commit finished slices to master (`ref #<ticket>`); park unfinished work with
  `tools/wayfinder/wayfinder-park.sh <note>` and record the stash ref in the packet
  before exiting. Signal the exit by writing or revising your packet — a stop
  with no handoff activity reads as a crash and gets nudged, then pauses the
  chain after 2 fruitless continuations.
- The daemon never stages, commits, or stashes — worktree hygiene belongs to
  the sessions.

## Hosted-CI repair watch (ref #627; replaces the retired local-CI watchdog #577) — DISABLED by default (ref #652)

The watch is off: `WAYFINDER_CI_REPAIR` defaults to `off`, so the daemon performs no hosted check-runs polling and mints no repair tickets. Session-start CI reconciliation (root `AGENTS.md`, "Performance") is the repair signal — a RED HEAD is repaired first by the session that observes it. Set `WAYFINDER_CI_REPAIR=on` in the daemon environment to restore the reconciling behavior described below.

When enabled, the daemon owns the hosted verification watch. On its normal session/doc ticks
— at most every `WAYFINDER_CI_WATCH_SECS` (default 300s) — it reconciles
current HEAD against hosted check-runs; active workers never poll hosted CI
themselves, and nothing is ever launched locally. The verdict predicate
mirrors session-start reconciliation exactly: RED is any concluded run that is
not success. A HEAD with runs still in flight is PENDING; a HEAD hosted CI
never ran (path-scoped-out pushes) records no verdict at all.

Each poll applies this table:

| State | Daemon action | Frontier effect |
|---|---|---|
| HEAD already has a recorded PASS/FAIL | nothing (dedupe) | none |
| hosted verdict GREEN | record PASS | none |
| hosted verdict PENDING, UNKNOWN, or gh unusable | nothing; retry next poll | none; never blocks on missing evidence |
| hosted verdict RED, first time for this SHA | create exactly one marker-bearing `wayfinder:task` repair issue | frontier gated until repair closes |

On a red verdict, the daemon first verifies `gh auth status` in its own
environment. With valid auth it finds or creates exactly one marker-bearing
`wayfinder:task` repair issue for that SHA, attaches it to this map as a native
child, and adds the repair issue's database ID as a native `blocked_by`
dependency of every currently claimable ordered child. Blocking every current
frontier row matters: blocking only the first row would let the next row bypass
repair-first. The SHA-to-issue mapping is persisted in daemon state, and the
GitHub marker is the restart-safe dedupe key. A failed auth/API write remains
unblocked and is retried by the daemon; it is never converted into a false
green verdict. A green result creates no tracker issue and does not close an
older red repair ticket. When there is no claimable child, the repair issue is
left as an unblocked map child for the next frontier query.

This watch launches no local workload; it adds no hosted workflow,
schedule, hook gate, or cross-machine claim. The existing `flock` remains the
single-daemon guard. The implementation lives in the canonical
`tools/wayfinder/wayfinder-loop.sh`; `scripts/wayfinder-loop.sh` stays as the
stable compat launcher until the live daemon is safely restarted — subsequent
restarts should use the canonical path; do not restart the live
loop as part of a relocation.

## Recovery semantics (#355)

All in-place recovery paths — the immediate-stop nudge, the stall/zombie
resume, and manual `--resume` — post the **same canonical recovery prompt**
(the script's `NUDGE`); no path keeps independent wording. It rehydrates
GitHub authority, excludes session-start-only CI reconciliation, preserves
the current claim and phase, allows required derivative-issue creation, and
routes human decisions through tracker issues (`needs-info` /
`ready-for-human`) — never the question tool.

- **Existing session → resume in place** (`--resume <session-id>`). Never a
  fresh spawn while the recorded session may still be alive.
- **Confirmed-gone session → fresh respawn** (`--retry`). It refuses to run
  while the recorded session (or its sub-agents) is still in the active set,
  so a stalled worker can never be silently duplicated.
- **Recovery budgets are consecutive-fruitless, not cumulative** (#422 loop
  hardening). Each recovery class keeps a small cap on *back-to-back fruitless*
  attempts, but the counter resets the moment the session shows real work — a
  completed tool-call turn newer than the nudge that preceded it. A session a
  nudge genuinely unstuck can be recovered again indefinitely; only a session
  that stays wedged with zero progress reaches its cap and pages the operator.
  The truncated-provider class (`provider.invalid-output`) is exempt from caps
  entirely. A tool call still in flight also suspends the stall detector: a
  long compile/test run freezes model-token output for its whole duration and
  is work, never a stall.
- Session-side duties on receiving a recovery prompt are specified in
  `docs/agents/wayfinder-lifecycle.md`.
- **Wedge self-heal.** The prompt tells a repeatedly-nudged session with no
  frontier progress to stop re-querying and diagnose the loop itself as the
  bug (daemon log tail, recorded state, clean worktree, canonical
  `wayfinder-*-handoff.md` packet name) — fixing the wedge IS the work when
  the chain is wedged. Packet renames are the classic instance: any other
  filename is invisible to the watcher and reads as `stopped without handoff`
  forever, while each re-query counts as tool work and clears the
  fruitless-attempt budget into an unbounded nudge cycle.

## Playbook

**Empty frontier drives the gate** — a session whose map frontier is empty claims one open external gate instead of exiting (full rule: `docs/agents/issue-tracker.md`, "Frontier query"). The gate claim is that session's one claim. Never flag a gate-driving session as stalled, and never route around an assigned or human-deferred gate.

**Chain looks stalled** — diagnose from `.wayfinder-loop.log` + `git status --porcelain`:

| Log line | Meaning | Action |
|---|---|---|
| `dirty worktree from … — sending recovery prompt` | owner session left the tree dirty | none — daemon NUDGEs the owner to commit or park, unbounded |
| `spawn paused … clean worktree` | owner session is gone with a dirty tree | commit or `wayfinder-park.sh`; resumes automatically |
| `waiting for session … to exit` | normal supervision, worker alive | check the session's tokens via `/api/session/<id>` before assuming stall |
| `stalled … resuming` | zombie detector firing | unbounded — every stall gets the NUDGE forever; a wedged session is the operator's call |
| `chain paused` | terminal assistant error (auth/quota-class) or config failure | fix cause, then restart (below) |
| `transient provider error — sent recovery prompt` | truncated model stream (`provider.invalid-output`) | none — daemon nudges every new failed turn, never pauses |
| session died without handoff | worker gone before writing its packet | none — daemon respawns fresh for the same packet, unbounded; repeated notifications on one packet = poison packet, inspect manually |
| repeated `stopped without handoff` + `worked since last recovery` with no handoff detected | wedge: packet invisible (non-canonical filename), dirty tree, or poison packet — each re-query clears the fruitless budget | fix per the prompt's wedge self-heal (canonical `wayfinder-*-handoff.md` name, commit/park, reconcile map vs native state), verify, write the successor packet, stop |
| nothing new + empty active set | daemon dead | restart (below) |

**Duplicate sessions on one packet** — quiesce the daemon first
(`tmux kill-session -t wayfinder-loop`). Prompt each extra session to stand
down (`POST /api/session/<id>/prompt`, explicit "do not touch any stash"),
wait for them to leave `/api/session/active`, then `wayfinder-park.sh`, then
restart.

**Restart the daemon**:

```bash
# fresh chain from a packet (resees all fingerprints from disk content)
# --bootstrap refuses while any live chain worker exists (kill-plus-bootstrap
# orphans the old worker into a duplicate) — plain restart resumes instead.
tmux new-session -d -s wayfinder-loop \
  'bash tools/wayfinder/wayfinder-loop.sh --bootstrap <packet-filename>'

# resume supervision of the session recorded in .wayfinder-loop.state
tmux new-session -d -s wayfinder-loop 'bash tools/wayfinder/wayfinder-loop.sh'
```

Verify green: one `created ses_…` line in the log, no `handoff … detected`
line within the following ticks, exactly one extra entry in
`GET /api/session/active`.
