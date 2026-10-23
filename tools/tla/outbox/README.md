# Offline save queue: TLA+ model

`Outbox.tla` models what a phone does with saves made while the signal is bad: the queue
(`web/src/lib/outbox.svelte.ts`), the 202 "saved on this phone" answer (`api/client.ts`
`resilientFetch`), the list cache that refreshes after a send (`lib/cache.svelte.ts`) and what
`#/today` shows (`pages/Today.svelte`). One person, one phone, one session, one product sale.
The server side is the rules of `SessionService.updateStatus`, `SessionRoutes.handleCreateSession`,
`ProductSaleService.sell` and `BranchDayService.assertEditableBranchDay`.

Last checked against the code at **ba7c4006** (master) plus the change that made `SessionService.updateStatus` ack a repeated mark. When any file named above changes after
that commit, reread the matching action in `Outbox.tla` before trusting a pass, then move the
commit forward here.

## Run it

```
tools/tla/outbox/run.sh                      # every config, a few minutes on 2 cores
tools/tla/outbox/run.sh today-NoSelfRefusal.cfg
```

Needs Java 17+. The script downloads `tla2tools.jar` (TLC 2026.10.20) into `~/.local/share/tla` once.

- `today-*.cfg` describe the code as it was on 62210247 (all fixes off). Each is **expected to fail** and prints the
  shortest counterexample, one line per step.
- `partial-*.cfg`: two fixes on (`DropNoopMark`, `ReplayReason`) so that the next bug shows. In
  today's code the lost update of bug 2 below is hidden behind bug 1's refusal; once bug 1 is
  fixed it would reach the server silently, and the model shows that.
- `fixed-*.cfg` switch on the fixes (`DropNoopMark`, `SkipRemoved`, `TriedKept`, `ChainVersions`,
  `RecentUntilSeen`, `ReplayReason`). They must pass.
- `noCapability-*.cfg`: the same fixes, but the signed-in person may not change earlier days. Their
  replays are refused with 403, never 400/409.

`run.sh` exits 1 if any config does the opposite of what its name says. `ablate.sh` switches the
fixes off one at a time in the passing configs and checks that the model then fails, so each fix is
shown to be needed (about ten minutes).

## Properties

| name | plain words |
| --- | --- |
| `NoVanish` | A session that exists today, on the server or waiting on the phone, is on `#/today`. |
| `NoSelfRefusal` | A save the person made in a sensible order is never refused with 400 or 409. |
| `ConvergesToIntent` | When nothing is left to send, the server holds the status the person chose last. |

## What it found (all four reproduce in the real app)

1. **Done, then Undo, both offline: 400.** The second tap replaces the first in the queue, leaving
   "Waiting" for a session that is Waiting. The server answers "Illegal session status transition
   from Waiting to Waiting". Test: `web/tests/outbox-model.e2e.ts` test 1.
2. **Undo while the Done is going out: the old one is sent anyway, the new one then clashes (409).**
   `flush()` walks the list it took when it started and the replaced save is still in it. The server
   ends on Done although the last tap was Waiting, and the phone shows "couldn't be sent: Session
   version mismatch". Test 2.
3. **A session sent from the phone vanishes from `#/today`.** The "just sent" row lived in memory for
   two minutes; a reload, or a list that could not be fetched in that time, drops it. Test 3.
4. **Saves replayed on a later day are refused 400.** A mark, session or sale made before the day
   turned over (04:00 Manila) is sent after it. The server asks for a reason on any earlier day and
   the replay carries none ("A reason is required to change a PAST day"). Checked on the status
   route against a running server; `web/tests/unit/replay.test.ts` covers the body the phone sends now.

Each fix is in the same change as its test; `fixed-*` shows the model agrees.

Later change: the queue no longer asks the server whether a refused mark had already landed
(`alreadyApplied`). `SessionService.updateStatus` answers 200 to a mark that repeats one that landed
(same status, version since moved on), so `Process` acks it and `Deliver` counts only 200. The
backend check is `OutboxReplayContractTest`, one replay test per queued save.

## Not modeled

Several sessions or branches; other people editing at the same time (the 409 "someone else changed
it" path is only checked for the person's own marks); clock-in gating of new sessions; expenses,
allowances and attendance saves (they take the same queue but have no ordering rule); the direct
online path of a save (modeled as enqueue-then-send); the 120 s / 24 h timers are only "later than
the refresh"; token expiry (401); 5xx. A pass covers one session, at most two marks, one reload,
one day turnover and two list requests in flight.

## Reading a counterexample

`trace.py` prints one line per step: the action, and the variables that changed. `queue` is the
phone's list of waiting saves, `srv` the server's session, `cache` the list on screen, `recent` the
"just sent" memory. Action names match `Outbox.tla` (`UserMark`, `FlushNext`, `Process` = the server
answers, `Deliver` = the answer reaches the phone, `ResLost` = it never does).
