# Sessions (Today)

Route `#/today`. The home page: today's sessions at the current branch, counts, who is clocked in, booked visits.

## Sub-features
- Counts row: Sessions, Done, Waiting, Session income, Product commission, Product sales.
- Tabs All / Waiting / Done / No-show / Voided.
- "Who's clocked in" panel with Clock in / Clock out per person.
- Buttons: Refresh, Past days, Book a visit, Product sale, New session (only when clocked in).
- Off duty: banner "Clock in at <branch> to see and log today's sessions", and the day's sessions request answers 403.

## How to get to it (user POV)
Sign in; the app opens here. Branch picker is in the top bar (K6 Fixture Branch in the scratch DB).

## Driving it with `tools/verify/drive.mjs`
`tools/verify/verify.sh shot '#/today'`. The helper clocks in as `owner` first, so the shot shows the on-duty page.
`VERIFY_CLOCKIN=0` shows the off-duty page instead (the Today screen read answers without a sessions part).
To add a session, copy `doneSessionForNewClient` from `web/tests/support.ts`: New session, search a name, New client, fill, Add session, Done.

## Gotchas
- Seed has no clients or sessions: counts are 0 and the list says "No sessions yet today." That is the real empty state, not a failure.
- Found by this harness (checked): after clocking in on `#/today` without reloading, the banner "Clock in at <branch> to see and log today's sessions" and "Who's clocked in 0 of 2 in" stay stale for 10s+ while the top bar already says "On duty" and "New session" appears (probe: 3 samples at 0, 3s, 11s). `drive.mjs` reloads after clocking in so shots show the settled page. Not fixed here.
- Found by this harness (checked, desktop 1400px): in the "Who's clocked in" panel the owner's "Clock out" button is cut off at the panel edge.
- Phone: the top bar shrinks the branch name ("K6 Fixt"); that is current behavior.
