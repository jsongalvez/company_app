#!/usr/bin/env bash
# wayfinder-loop.sh — unattended wayfinder chain driver for opencode2.
#
# Watches docs/agents/ for new wayfinder-*-handoff.md files (the chain's
# completion signal), spawns a fresh 0-context opencode2 session that reads
# the newest handoff and drives the next session per its instructions, and
# notifies the human when the agent parks on a question or the chain breaks.
#
# Usage:
#   wayfinder-loop.sh --bootstrap <doc>   first start: seed with <doc>, spawn immediately
#   wayfinder-loop.sh                     resume from state file (restart / crash recovery)
#
# Env:
#   WAYFINDER_NTFY_TOPIC   ntfy.sh topic for phone push (unset = desktop only)
#   WAYFINDER_POLL_SECS    doc poll interval (default 15)
#   WAYFINDER_DRY_RUN      non-empty = log transitions, never spawn
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DOCS_DIR="$REPO/docs/agents"
STATE_FILE="$REPO/.wayfinder-loop.state"
LOG_FILE="$REPO/.wayfinder-loop.log"
[ -f "$REPO/.wayfinder-loop.env" ] && set -a && . "$REPO/.wayfinder-loop.env" && set +a
NTFY_TOPIC="${WAYFINDER_NTFY_TOPIC:-}"
POLL_SECS="${WAYFINDER_POLL_SECS:-15}"
DRY_RUN="${WAYFINDER_DRY_RUN:-}"
OC_BIN="${OPENCODE_BIN:-$(command -v opencode2 || command -v opencode || true)}"

log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$LOG_FILE"; echo "$(date '+%T') $*"; }
die() { log "FATAL: $*"; notify "wayfinder-loop FAILED" "$*"; exit 1; }

notify() {
  local title="$1" body="$2"
  command -v notify-send >/dev/null 2>&1 && notify-send -a wayfinder-loop "$title" "$body" || true
  if [ -n "$NTFY_TOPIC" ]; then
    curl -sf -d "$body" -H "Title: $title" -H "Priority: high" "https://ntfy.sh/$NTFY_TOPIC" >/dev/null 2>&1 || true
  fi
}

handoff_docs() { find "$DOCS_DIR" -maxdepth 1 -name 'wayfinder-*-handoff.md' -printf '%f\n' 2>/dev/null | sort; }

api() { "$OC_BIN" api "$@"; }

load_state() {
  last_doc=""; session_id=""
  [ -f "$STATE_FILE" ] || return 0
  # shellcheck disable=SC1090
  source "$STATE_FILE"
}

save_state() {
  {
    echo "last_doc=$last_doc"
    echo "session_id=$session_id"
    echo "retries=${retries:-0}"
    echo "seen_docs=$seen_docs"
  } > "$STATE_FILE"
}

# seen_docs is a comma-separated list of processed handoff basenames
seen_docs=""
mark_seen() {
  if [ -n "$seen_docs" ]; then seen_docs="$seen_docs,$1"; else seen_docs="$1"; fi
}

is_new_doc() {
  # $1 = basename; true if not in seen_docs
  case ",$seen_docs," in
    *",$1,"*) return 1 ;;
    *) return 0 ;;
  esac
}

newest_unprocessed() {
  local d
  for d in $(handoff_docs); do
    if is_new_doc "$d"; then echo "$d"; return 0; fi
  done
  return 1
}

pending_forms() { api get "/api/session/$1/form" 2>/dev/null | jq -r '.data[] | select(.metadata.kind == "question") | .id' 2>/dev/null || true; }
pending_perms() { api get "/api/session/$1/permission" 2>/dev/null | jq -r '.data[] | .id' 2>/dev/null || true; }
session_alive() { api get "/api/session/active" 2>/dev/null | jq -e --arg s "$1" '.data | any(.id == $s)' >/dev/null 2>&1; }

spawn_session() {
  local doc="$1" sid
  if [ -n "$DRY_RUN" ]; then
    log "DRY-RUN: would spawn session for $doc"
    return 1
  fi
  sid="$(api post /api/session --data "$(jq -nc --arg d "$doc" --arg dir "$REPO" \
    '{title: ("wayfinder-loop: " + $d), location: {directory: $dir}}')" | jq -r '.data.id' || true)"
  [ -n "$sid" ] && [ "$sid" != "null" ] || die "session create failed for $doc"
  if [ -z "$DRY_RUN" ] && [ -z "${WAYFINDER_ALLOW_DIRTY:-}" ]; then
    if [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null | head -1)" ]; then
      log "worktree dirty — spawn paused for $doc"
      notify "wayfinder paused" "worktree has uncommitted changes — commit or stash; the chain resumes automatically"
      while [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null | head -1)" ]; do sleep 60; done
      notify "wayfinder resumed" "worktree clean — spawning $doc"
      log "worktree clean — spawn resumes"
    fi
  fi
  session_id="$sid"
  save_state
  local prompt
  prompt="Fresh session with zero prior context. You are driven by an unattended chain runner.

Read docs/agents/$doc — the latest wayfinder handoff — and follow its \"How to drive the next session\" instructions exactly (load the wayfinder skill, claim before work, one ticket per session, map #89 discipline).

Operating rules for this automated run:
1. Work autonomously as far as you can. Prefer AFK-capable work from the map's own \"Recommended next pick\" when no human decision is truly needed.
2. If you need a human decision (grilling, fog-graduation pick, HITL design), ask via the question tool and WAIT. Do not guess.
3. When done, write docs/agents/wayfinder-<N>-handoff.md (next session number) following the existing format. That file is the chain's completion signal — it MUST exist before you stop.
4. Then stop. Do not start follow-up work."
  api post "/api/session/$sid/prompt" --data "$(jq -nc --arg t "$prompt" '{text: $t}')" >/dev/null || die "prompt failed for session $sid"
  log "spawned $sid reading $doc"
  notify "wayfinder session started" "session $sid — reading $doc"
}

wait_idle() {
  # 0 = idle, 124 = still running/blocked (bounded wait fired), 1 = API error
  timeout 180 api post "/api/session/$1/wait" >/dev/null 2>&1
  case $? in
    0) return 0 ;;
    124) return 124 ;;
    *) return 1 ;;
  esac
}

session_dead() {
  # resume: the session still exists but stalled/interrupted — ask it to continue in place
  # fresh: session gone (404) — spawn a new one from the handoff doc
  local mode="${1:-fresh}"
  if [ "$mode" = "resume" ]; then
    if [ "${retries:-0}" -ge 2 ]; then
      log "chain paused: $session_id stalled after $retries resume attempts"
      notify "wayfinder chain paused" "session $session_id stalled after 2 resume attempts — attach TUI to check, or run: ./scripts/wayfinder-loop.sh --retry"
      exit 0
    fi
    retries=$(( ${retries:-0} + 1 ))
    save_state
    log "resuming stalled session $session_id (attempt $retries/2)"
    notify "wayfinder resuming" "session $session_id stalled — asking it to continue where it left off"
    if ! api post "/api/session/$session_id/prompt" --data "$(jq -nc '{text: "You were interrupted mid-session. Continue exactly where you left off, per your session instructions. Do not restart or re-read the handoff doc unless required."}')" >/dev/null 2>&1; then
      log "resume prompt failed for $session_id — falling back to fresh spawn"
      session_dead fresh
    fi
    return 0
  fi
  if [ "${retries:-0}" -ge 2 ]; then
    log "chain paused: $session_id died without handoff (retries=$retries)"
    notify "wayfinder chain paused" "session $session_id died without writing a handoff. Run: ./scripts/wayfinder-loop.sh --retry"
    exit 0
  fi
  retries=$(( ${retries:-0} + 1 ))
  session_id=""
  save_state
  log "session died without handoff — respawn for $last_doc (retry $retries/2)"
  notify "wayfinder retrying" "session died — spawning a fresh session for $last_doc (retry $retries/2)"
  spawn_session "$last_doc" || { log "dry-run retry"; exit 0; }
}

wait_for_doc() {
  local d
  while :; do
    d="$(newest_unprocessed || true)"
    if [ -n "$d" ]; then
      # settle: the file may still be mid-write
      sleep 10
      last_doc="$d"
      mark_seen "$d"
      save_state
      if spawn_session "$d"; then
        return 0
      fi
      log "dry-run: would continue supervising $d"
      exit 0
    fi
    sleep "$POLL_SECS"
  done
}

supervise_session() {
  local notified=0 notified_perm=0 outages=0 rc idle_ticks=0 last_count=-1 count
  while :; do
    rc=0; wait_idle "$session_id" || rc=$?
    if [ $rc -eq 124 ]; then
      # wait bounded = still running or zombie; check for parked question/permission
      local f p
      f="$(pending_forms "$session_id")"
      p="$(pending_perms "$session_id")"
      if [ -n "$f" ] && [ "$notified" -eq 0 ]; then
        notify "wayfinder needs you" "session $session_id is asking a question — attach TUI and answer"
        notified=1
      fi
      if [ -z "$f" ] && [ "$notified" -eq 1 ]; then notified=0; fi
      if [ -n "$p" ] && [ "$notified_perm" -eq 0 ]; then
        notify "wayfinder needs permission" "session $session_id blocked on a permission request — attach TUI to approve"
        notified_perm=1
      fi
      if [ -z "$p" ] && [ "$notified_perm" -eq 1 ]; then notified_perm=0; fi
      # zombie detection: a live loop appends messages (tool results included);
      # three wait slices (~9 min) without growth = stalled, treat as dead
      count="$(api get "/api/session/$session_id/message" 2>/dev/null | jq -r '.data | length' 2>/dev/null || echo -1)"
      if [ "$count" -ge 0 ] && [ "$count" -eq "$last_count" ]; then
        idle_ticks=$((idle_ticks+1))
      else
        idle_ticks=0
      fi
      [ "$count" -ge 0 ] && last_count="$count"
      if [ "$idle_ticks" -ge 3 ]; then
        log "session $session_id stalled (no message growth across 3 wait slices) — resuming in place"
        session_dead resume
        continue
      fi
      continue
    fi
    if [ $rc -eq 1 ]; then
      # API error: service down or session gone
      if api get "/api/session/$session_id" >/dev/null 2>&1; then
        outages=$((outages+1))
        if [ $outages -eq 3 ]; then
          notify "opencode2 API unstable" "daemon will keep retrying — check 'opencode2 service status'"
          outages=0
        fi
        sleep 30
      else
        session_dead fresh
      fi
      continue
    fi
    # rc=0: loop idle — classify
    outages=0
    local d
    d="$(newest_unprocessed || true)"
    if [ -n "$d" ]; then
      log "session $session_id completed; next handoff: $d"
      notify "wayfinder session done" "handoff written: $d — starting next"
      last_doc="$d"
      mark_seen "$d"
      retries=0
      save_state
      spawn_session "$d" || { log "dry-run: chain would continue"; exit 0; }
      return 0
    fi
    # no doc: blocked on a question answered via TUI, or parked on permission, or dead
    if [ -n "$(pending_forms "$session_id")" ]; then
      [ "$notified" -eq 0 ] && { notify "wayfinder needs you" "session $session_id is asking a question — attach TUI and answer"; notified=1; }
      while [ -n "$(pending_forms "$session_id")" ]; do sleep 20; done
      notified=0
      log "session $session_id question answered — resuming"
      continue
    fi
    if [ -n "$(pending_perms "$session_id")" ]; then
      [ "$notified_perm" -eq 0 ] && { notify "wayfinder needs permission" "session $session_id blocked on a permission request — attach TUI to approve"; notified_perm=1; }
      while [ -n "$(pending_perms "$session_id")" ]; do sleep 20; done
      notified_perm=0
      log "session $session_id permission granted — resuming"
      continue
    fi
    if session_alive "$session_id"; then
      continue
    fi
    session_dead resume
  done
}

[ -n "$OC_BIN" ] || die "opencode2 binary not found"
[ -d "$DOCS_DIR" ] || die "docs/agents not found at $DOCS_DIR"
command -v jq >/dev/null 2>&1 || die "jq not found"

load_state

if [ "${1:-}" = "--bootstrap" ]; then
  [ $# -ge 2 ] || die "--bootstrap requires <doc> (e.g. wayfinder-162-handoff.md)"
  doc="$2"
  [ -f "$DOCS_DIR/$doc" ] || die "bootstrap doc not found: $DOCS_DIR/$doc"
  # seed: every existing handoff is seen; the bootstrap doc spawns immediately
  seen_docs="$(handoff_docs | paste -sd, -)"
  last_doc="$doc"
  mark_seen "$doc"
  retries=0
  save_state
  log "bootstrap with $doc — spawning first session"
  if ! spawn_session "$doc"; then
    log "dry-run bootstrap complete"
    exit 0
  fi
  supervise_session
elif [ "${1:-}" = "--retry" ]; then
  [ -n "$last_doc" ] || die "--retry needs a processed doc in state (bootstrap first)"
  session_id=""
  retries=0
  save_state
  log "manual retry for $last_doc"
  spawn_session "$last_doc" || exit 0
  supervise_session
else
  [ -n "$last_doc" ] || die "no state — first run needs: --bootstrap <doc>"
  if [ -n "$session_id" ]; then
    log "resuming supervision of $session_id"
    supervise_session
  fi
  wait_for_doc
  supervise_session
fi
