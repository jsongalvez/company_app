# Exposed Migration Ticket Tracking

Bump Exposed from 0.61.0 to 1.3.1. Wide refactor — expand phase first (mechanical imports), then manual API fixes, then verify.

Work "blocked-by" first — any unblocked ticket is ready to grab.

## Blocking edges

| Ticket | Blocked by | Status |
|--------|-----------|--------|
| EM-01 — Bump Exposed + mechanical import migration | — | [ ] |
| EM-02 — Manual API fixes (enum, UUID, transaction, custom column) | EM-01 | [ ] |
| EM-03 — Full test suite verification + docs update | EM-01, EM-02 | [ ] |

## To mark done

Append `\n**Status:** ✅ done` to the bottom of the ticket file, then check `[x]` above.
