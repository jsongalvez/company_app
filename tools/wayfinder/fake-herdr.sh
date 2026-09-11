#!/usr/bin/env bash
# fake-herdr.sh — deterministic stub Herdr CLI for Wayfinder parallel-runtime
# contract tests (map #697, ticket #743).
#
# Exercises lifecycle logic without real interactive panes or live Muse
# sessions. State is file-driven under FAKE_HERDR_DIR so tests script exact
# sequences: working, idle/done, blocked-input, waiting/resource,
# crash/disappearance, prompt/read behavior, and pane/agent discovery during
# reconciliation.
#
# Usage: HERDR_BIN=tools/wayfinder/fake-herdr.sh <wayfinder script> ...
#
# Env:
#   FAKE_HERDR_DIR   state directory (default: ${WORK:-/tmp}/fake-herdr).
#                    Per-agent files inside it:
#                      status-<name>      canned status for `agent get`
#                                         (default: running)
#                      output-<name>      canned output for `agent read`
#                                         (default: screen-output)
#                      fail-start-<name>  when present, `agent start <name>`
#                                         fails (injected start failure)
#                      fail-prompt-<name> when present, `agent prompt <name>`
#                                         fails (injected prompt failure)
#                      list.json          canned `agent list` payload
#                                         (default: {"result":{"agents":[]}})
#   HERDR_CALLS        call log (default: $FAKE_HERDR_DIR/calls). Every
#                    invocation appends its argv line; tests assert dispatch
#                    shape (async: no --wait), prompt carriage, and counts.
#                    FAKE_HERDR_CALLS is accepted as an alias.
#
# Subcommands (mirrors the worker seam's Herdr surface only):
#   pane split ...            allocate a pane (always pane-7)
#   agent start <name> ...    start an agent (records initial running state
#                             unless a status file already exists)
#   agent prompt <name> ...   deliver a prompt (records the prompt text to
#                             prompt-<name> for carriage assertions)
#   agent get <name>          report the canned status for one agent
#   agent list                report the canned agent list (pane/agent
#                             discovery during reconciliation)
#   agent wait <name> ...     bounded check-in (always reports done)
#   agent read <name> ...     return the canned terminal output
#   agent send-keys <name> .. interrupt the agent (stop path)
#   pane close <pane_id>  release the pane (cleanup path, best-effort —
#                             the agent dies with its pane; there is no
#                             `agent delete` verb in the installed Herdr
#                             CLI, ticket #899)
#
# Crash/disappearance is simulated by omitting the agent from list.json (the
# seam marks the row gone, row kept for salvage) or by deleting its status
# file. blocked-input is a canned `blocked` status; waiting/resource is
# observed through the capacity seam's heavy-acquire refusal, never as a
# Herdr status — the two must stay distinct (see wayfinder-parallel-test.sh).
set -euo pipefail

STATE_DIR="${FAKE_HERDR_DIR:-${WORK:-/tmp}/fake-herdr}"
CALLS="${HERDR_CALLS:-${FAKE_HERDR_CALLS:-$STATE_DIR/calls}}"
mkdir -p "$STATE_DIR"
: >> "$CALLS"
printf '%s\n' "$*" >>"$CALLS"

agent_status() { # <name> — canned status, default running.
    local name="$1" f="$STATE_DIR/status-$name"
    if [ -f "$f" ]; then
        cat "$f"
    else
        printf 'running'
    fi
}

case "${1:-} ${2:-}" in
    "pane split")
        printf '{"result":{"pane":{"pane_id":"pane-7"}}}'
        exit 0
        ;;
    "agent start")
        name="${3:-}"
        [ -n "$name" ] || { printf 'fake-herdr: agent start requires a name\n' >&2; exit 2; }
        if [ -f "$STATE_DIR/fail-start-$name" ]; then
            printf 'injected start failure\n' >&2
            exit 1
        fi
        [ -f "$STATE_DIR/status-$name" ] || printf 'running' >"$STATE_DIR/status-$name"
        printf '{"result":{"agent":{"name":"%s","status":"running"}}}' "$name"
        exit 0
        ;;
    "agent prompt")
        name="${3:-}"
        [ -n "$name" ] || { printf 'fake-herdr: agent prompt requires a name\n' >&2; exit 2; }
        if [ -f "$STATE_DIR/fail-prompt-$name" ]; then
            printf 'injected prompt failure\n' >&2
            exit 1
        fi
        printf '%s' "$*" >"$STATE_DIR/prompt-$name"
        exit 0
        ;;
    "agent get")
        name="${3:-}"
        [ -n "$name" ] || { printf 'fake-herdr: agent get requires a name\n' >&2; exit 2; }
        printf '{"result":{"agent":{"name":"%s","status":"%s"}}}' "$name" "$(agent_status "$name")"
        exit 0
        ;;
    "agent list")
        if [ -f "$STATE_DIR/list.json" ]; then
            cat "$STATE_DIR/list.json"
        else
            printf '{"result":{"agents":[]}}'
        fi
        exit 0
        ;;
    "agent wait")
        name="${3:-}"
        [ -n "$name" ] || { printf 'fake-herdr: agent wait requires a name\n' >&2; exit 2; }
        printf '{"result":{"agent":{"name":"%s","status":"done"}}}' "$name"
        exit 0
        ;;
    "agent read")
        name="${3:-}"
        [ -n "$name" ] || { printf 'fake-herdr: agent read requires a name\n' >&2; exit 2; }
        if [ -f "$STATE_DIR/output-$name" ]; then
            cat "$STATE_DIR/output-$name"
        else
            printf 'screen-output\n'
        fi
        exit 0
        ;;
    "agent send-keys")
        exit 0
        ;;
    "pane close")
        exit 0
        ;;
esac
printf 'fake-herdr: unexpected invocation: %s\n' "$*" >&2
exit 1
