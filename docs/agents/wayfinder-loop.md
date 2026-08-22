# Wayfinding chain daemon

Unattended driver for the wayfinder handoff chain: a tmux session
(`wayfinder-loop`) running `scripts/wayfinder-loop.sh`. It watches
`.wayfinder/handoffs/` for new `wayfinder-*-handoff.md` packets, spawns a
fresh zero-context opencode2 session to execute each one, supervises the
session (questions, permissions, stalls), and chains to the next packet.

Runtime files (gitignored): `.wayfinder-loop.state` (last doc, session id,
seen-doc fingerprints, retries), `.wayfinder-loop.log`, `.wayfinder-loop.lock`.

## Rules

- **Quiesce the daemon before touching sessions or the worktree.** The stop
  detector re-prompts any session that stops without a handoff — an operator
  killed duplicate looks like a stalled worker and gets resumed against you.
- **One packet, one filename, forever.** A successor link is a NEW file.
  Editing a queued or active packet is absorbed (fingerprint updated, no
  chaining) since #336, but corrections belong in a new file.
- **End every chain session with a clean worktree.** Commit finished slices
  to master (`ref #<ticket>`); park unfinished work with
  `scripts/wayfinder-park.sh <note>` and record the stash ref in the packet
  before exiting. A dirty tree freezes the spawn gate.
- The daemon never stages, commits, or stashes — worktree hygiene belongs to
  the sessions.

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
- Session-side duties on receiving a recovery prompt are specified in
  `docs/agents/wayfinder-lifecycle.md`.

## Playbook

**Chain looks stalled** — diagnose from `.wayfinder-loop.log` + `git status --porcelain`:

| Log line | Meaning | Action |
|---|---|---|
| `spawn paused … clean worktree` | dirty tree holds the gate | commit or `wayfinder-park.sh`; resumes automatically |
| `waiting for session … to exit` | normal supervision, worker alive | check the session's tokens via `/api/session/<id>` before assuming stall |
| `stalled … resuming (attempt n/2)` | zombie detector firing | after 2 attempts it pings and exits 0 — inspect the session manually |
| `chain paused` | retries exhausted or terminal assistant error (auth/quota-class) | fix cause, then restart (below) |
| `transient provider error — sent recovery prompt` | truncated model stream (`provider.invalid-output`) | none — daemon nudged the session; only repeated failures (2 resumes) pause |
| nothing new + empty active set | daemon dead | restart (below) |

**Duplicate sessions on one packet** — quiesce the daemon first
(`tmux kill-session -t wayfinder-loop`). Prompt each extra session to stand
down (`POST /api/session/<id>/prompt`, explicit "do not touch any stash"),
wait for them to leave `/api/session/active`, then `wayfinder-park.sh`, then
restart.

**Restart the daemon**:

```bash
# fresh chain from a packet (resees all fingerprints from disk content)
tmux new-session -d -s wayfinder-loop \
  'bash scripts/wayfinder-loop.sh --bootstrap <packet-filename>'

# resume supervision of the session recorded in .wayfinder-loop.state
tmux new-session -d -s wayfinder-loop 'bash scripts/wayfinder-loop.sh'
```

Verify green: one `created ses_…` line in the log, no `handoff … detected`
line within the following ticks, exactly one extra entry in
`GET /api/session/active`.
