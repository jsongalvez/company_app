# ADR-0022: Pessimistic inline editing for shared dashboard data

**Status:** Accepted
**Date:** 2026-07-21

## Context

The session dashboard (#97) is a shared-day view: multiple practitioners and
coordinators at the same branch see the same sessions, refreshed by 30-second
polling (spec line 107). US-16 (spec line 55) requires inline editing on
desktop — click a cell, edit type/status/price, PATCH the backend.

The fork: does the UI update the displayed value optimistically (immediately,
before the backend confirms) or pessimistically (only after the backend
confirms success)?

This is a behavioral contract that multiple future features will implicitly
depend on, not a disposable implementation choice. It sits on the same
"backend is authoritative" axis already established by ADR-0021 (401 token
clearing), #92 (403 capability refresh), and #94 (launch validation via
`GET /api/me`).

## Decision

Inline edits are **pessimistic**. The displayed value is not updated until
the backend confirms success. During the PATCH request, the cell shows a
loading state (dimmed input + small spinner) and retains the original
displayed value. On success, the cell exits edit mode and shows the new
value. On failure, the user's attempted value stays in the input with an
inline error, and the user can retry or discard (Esc → original).

## Consequences

These behaviors follow from the pessimistic model. They are not co-equal
decisions — they are the inline-edit expression of the "backend is
authoritative" axis applied to each failure / concurrency path.

- **Active edits temporarily own their field against polling.** While a cell
  is in edit mode (including PATCH in flight), polling does not overwrite
  that field. Other rows and non-edited fields continue updating normally.
  The user's draft temporarily owns the cell; polling respects that
  ownership until commit or discard.
- **403 revocation terminates the edit without reconciling optimistic
  state.** A 403 mid-edit means the capability was revoked (per #92's
  trigger-driven refresh). The cell exits edit mode, the draft is
  discarded, and the edit affordance vanishes (silent — #92's per-element
  guard). There is no optimistic value to roll back; the backend's
  authoritative state is what remains on screen.
- **409 conflict handling operates before any committed UI change.** A
  version conflict (per `engines.md:187`) is surfaced inline with a Reload
  action. Because the UI never optimistically committed the edit, there is
  no optimistic state to reconcile — reload simply shows the backend's
  current authoritative state, with an indication of which fields changed
  remotely.
- **Polling remains backend-authoritative for all committed data.** The
  pessimistic model means polling and committed edits never disagree: a
  committed edit is, by definition, what the backend returned, and the next
  poll returns the same value (until someone else edits). An optimistic
  model would create a window where the displayed value (optimistic) and
  the polled value (backend) could diverge, forcing reconciliation logic.

## Accepted cost

The UI feels slightly less responsive than optimistic editing — the user
clicks, waits for the PATCH, then sees the update. For financial values
(finalPrice) on a shared-day dashboard, this is the explicit trade for
trust: an optimistic update that gets rolled back (PATCH fails, or a
concurrent edit 409s) would flicker against the next 30s poll, eroding
confidence in the displayed numbers. The pessimistic model is the
inline-edit expression of the same axis that made #94 validate the token
via `GET /api/me` on launch rather than trusting cached state.

## Alternatives considered

**Optimistic updates.** Update the displayed value immediately, PATCH in
the background, roll back on failure. Feels more responsive. Rejected
because (a) a shared-day view with 30s polling means an optimistic value
that gets rolled back would flicker against the next poll — the displayed
value would change, change back, then change again when the poll arrives;
(b) financial values are the kind of data where "it updated then
un-updated" erodes trust; (c) it would break the "backend is authoritative"
axis already established by ADR-0021 / #92 / #94, introducing a path where
the UI acts on what it *hopes* the backend will say rather than what the
backend *says*. The responsiveness gain is not worth the reconciliation
complexity and the trust cost on a financial shared-view.
