# ADR-0026: A client waits in one queue per day, not one forever

**Status:** Accepted
**Date:** 2026-10-30

## Context

A client could have one Waiting session at a time, across all days and branches (`idx_client_one_pending_session` on `client_id`, plus the same check in `SessionRepository`). The rule exists to stop one person standing in two queues at once.

Feedback d6cc83cf (user, 2026-10-30): "The waiting for a client to be marked done in another branch is unfeasible. Practioners generally only beling to one branch. In the first place, a client that is waiting in a previous day could only have been done via someone inputting past sessions or someone forgetting to mark a session. It makes no sense to have to encounter this issue." Juan dela ceiz could not be added because a session from Oct 28 at Alabang was still Waiting.

A queue exists only on its day. A Waiting session on an earlier day is a stale record: entered late, or never marked. It says nothing about where the client is today.

## Decision

- A client may have one Waiting session per day, across all branches. Adding a session, setting one back to Waiting, and restoring a voided Waiting session check only the target session's day (`SessionRepository.assertNotWaitingElsewhereInTransaction`). The check runs under the client row lock that every one of those commands takes first, so two branches can't both win on the same day.
- V24 replaces the global unique index with `idx_client_one_pending_session_per_day` on `(client_id, branch_day_id)`, a backstop for one branch day. The cross-branch same-day rule lives in code, because `session` holds no date.
- A stale Waiting session is not hidden or changed automatically. The Today screen lists the earlier unremitted days at the branch that still hold one (`TodayScreenResponse.earlierWaitingDays`, for anyone who may see the branch), and each date opens that day so the branch marks it, with a reason, as for any earlier-day change.
- Anonymizing a client still refuses while any Waiting session exists, on any day: a queued client keeps a name.

## Consequences

- The block in d6cc83cf is gone, and the stale session stays in view until someone marks it.
- Remitted days are left out of the list. A remitted day's sessions change only with coordinator rights.
- The Today read takes one more query (budget 12 to 13 in `TodayScreenServicePostgresTest`).
