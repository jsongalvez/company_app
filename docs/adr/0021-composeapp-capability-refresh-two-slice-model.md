# ADR-0021: ComposeApp capability refresh — two-slice model

**Status:** Accepted
**Date:** 2026-07-21

## Context

ADR-0020's host-level split (`expect fun AppNavHost`, `App()` does shared setup
then calls the expect host) and #92's `SessionState` singleton together raise a
sequencing question for capability fetching across the login → BranchSelect →
clock-in → Dashboard flow.

#92 established *that* capabilities are branch-scoped (contextType `BRANCH`,
contextId = `selectedBranchId`) and *when* to refresh them (trigger-based:
login, clock-in, 403 — no polling, capabilities are identity not data). Spec
line 103 mandates that "on login success, the frontend calls `GET /api/me` and
`GET /api/me/capabilities` and stores the result in a `SessionState` object
(StateFlow, available app-wide)."

But neither #92 nor the spec resolved the *sequencing consequence* of
branch-scoping: at login time (before clock-in), `SessionState.selectedBranchId`
is null, so `GET /api/me/capabilities` returns capabilities across all
contexts — the **global slice** (`MANAGE_USERS`, `ASSIGN_DELEGATE`) is usefully
resolvable, but the **branch-scoped slice** (`EDIT_BRANCH_DATA` on branch X,
`SUBMIT_REMITTANCE` on branch Y) is present in the response yet not usefully
resolvable until `selectedBranchId` is set at clock-in.

The fork: fetch capabilities once (only after clock-in, when the branch-scoped
slice is fully resolvable), or fetch twice (once at login/launch for the global
slice, once at clock-in for the branch-scoped slice)?

## Decision

Fetch capabilities **twice** across the flow, serving different slices:

1. **Pre-BranchSelect fetch** (login trigger or launch-validation trigger):
   calls `GET /api/me/capabilities` after `GET /api/me` succeeds. Populates
   `SessionState.capabilities` with the full response (global + branch-scoped
   rows), but only the **global slice** is usefully resolvable —
   `selectedBranchId` is null, so branch-scoped capabilities cannot be matched
   to a selected branch. This fetch is mandated by spec line 103 and populates
   `SessionState` so it is available app-wide from login, not just from
   clock-in.

2. **Post-clock-in fetch** (clock-in trigger, per #92): re-calls
   `GET /api/me/capabilities` after `POST /api/attendance/clock-in` succeeds
   and `SessionState.selectedBranchId` is set. Now the **branch-scoped slice**
   is usefully resolvable for the selected branch —
   `hasCapability(EDIT_BRANCH_DATA, BRANCH, selectedBranchId)` returns a
   meaningful answer.

The pre-BranchSelect fetch is **never treated as "capabilities fully
resolved."** It populates `SessionState.capabilities` (per spec + #92's
app-wide availability), and the global slice it resolves is enough for any
pre-clock-in UI that gates on global capabilities. But branch-scoped gating
(US-28 drawer filtering, Dashboard per-element guards via `uiState` flags) only
becomes correct after the post-clock-in fetch completes.

## Accepted cost

Two network calls instead of one. The pre-BranchSelect response contains the
branch-scoped rows that the post-clock-in response will also return — the
global slice is fetched twice, redundantly. This is the explicit trade for (a)
spec compliance (line 103 mandates the login-time fetch), (b) `SessionState`
being available app-wide from login rather than only from clock-in, and (c)
not landing the user on Dashboard with only the global slice (which would
break branch-scoped per-element gating — the exact bug the clock-in trigger
exists to prevent).

The redundancy is bounded: capabilities are identity, not data (per #92 — no
polling), so the two fetches happen only at login/launch and at clock-in, not
on a recurring interval.

## Same axis, applied elsewhere — token clearing on 401 vs network error

The 401-vs-network-error token-clearing rule from #94 rides the same axis #92
already named ("backend is authoritative" — act on what the backend actually
says, don't infer from silence or failure), applied to a different fetch
point. It is a **sibling** of this decision under that axis, not a consequence
of the two-slice model.

- **401 response** (backend says the caller is not authenticated):
  `ApiClient.onUnauthorized` emits → clear token → navigate to Login. The
  backend has made an authoritative statement about the token's validity;
  acting on it (clearing) is correct. At launch this is silent (user has taken
  no action); mid-session it carries a "session expired" message (user was
  interrupted).
- **Network error** (backend couldn't be reached): preserve the token. A
  network failure carries no information about the actual authorization state
  — the token might be valid, the network is the problem. Clearing a valid
  credential for a transient connectivity issue would be strictly worse than
  the thing being protected against, with no compensating benefit. The launch
  splash shows an error + Retry; the LoginScreen shows inline red text; the
  BranchSelect clock-in shows an inline error + retry.

This is the same principle #92 applied to the 403-refresh path: a failed
*refresh* (network problem) fell through to `UiState.Error` rather than being
treated as an authorization answer, because a network failure carries no
information about the actual authorization state. The token-clearing rule
applies that identically to token storage.

## Alternatives considered

**Defer all capability fetching to post-clock-in.** Skip the pre-BranchSelect
fetch entirely; call `GET /api/me/capabilities` only once, after clock-in when
`selectedBranchId` is set and the branch-scoped slice is fully resolvable. One
network call instead of two, no redundancy. Rejected because (a) spec line 103
explicitly mandates the login-time fetch, (b) #92's design has
`SessionState.capabilities` available app-wide from login — deferring would
leave it null until clock-in, breaking any pre-clock-in reader, and (c) the
global slice (drawer's global items, any future pre-clock-in capability-gated
UI) would be unavailable at login. The redundancy cost is bounded (capabilities
are identity, not data — no polling), so the two-call cost is paid once per
login/launch + once per clock-in, not recurring.

**Fetch once at login, no clock-in refresh.** Call
`GET /api/me/capabilities` at login and treat the result as complete. Rejected
because #92 already established the clock-in trigger, and because landing on
Dashboard with only the global slice would break branch-scoped per-element
gating — `hasCapability(EDIT_BRANCH_DATA, BRANCH, selectedBranchId)` cannot
resolve when the capabilities were fetched before `selectedBranchId` existed.
This is the exact bug the post-clock-in refresh exists to prevent.
