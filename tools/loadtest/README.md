# Database load test

Finds the queries that grow badly as the data grows. It seeds a throwaway Postgres at 1x, 10x, 100x and 1000x of a small practice's first month, runs the real app against each, and times the hot routes through the app's own `Server-Timing` (db + app ms). It never touches the prod database: everything runs in the `company-loadtest-pg` container on `127.0.0.1:55433`, with its data on `/mnt/data/loadtest/pg`.

| Scale | Branches | Days of history | Clients | Sessions |
| --- | --- | --- | --- | --- |
| 1 | 3 | 30 | 600 | 1,800 |
| 10 | 9 | 95 | 6,000 | ~17,000 |
| 100 | 30 | 300 | 60,000 | 180,000 |
| 1000 | 95 | 949 | 600,000 | ~1.8M |

`seed.sql` grows width and history together (branches and days both by `sqrt(scale)`), with 20 sessions, 6 product sales and 3 expenses a day at each branch, daily attendance and pay, weekly remittances with their lines and day breakdowns, an audit row per change, a notification per session, bookings, telemetry and trace spans. The DevMain fixture branch is one of the branches, so `owner` and `staff` see a full history and an open Today.

## Commands

```
tools/loadtest/run.sh pg-up            # start the throwaway Postgres
tools/loadtest/run.sh fresh            # migrate an empty database once (template for every scale)
tools/loadtest/run.sh seed 100         # lt_x100 from the template, then seed.sql (1000x takes ~20 min)
tools/loadtest/run.sh app 100          # run this checkout's backend on :18401 against lt_x100 (Flyway applies new migrations)
tools/loadtest/run.sh bench 100 before 30   # 30 timed requests per route -> results/raw.jsonl, top statements -> results/statements-x100-before.tsv
tools/loadtest/run.sh stats            # median and 95% bootstrap CI per route, scale and label -> results/summary.csv
tools/loadtest/run.sh down
```

`bench.mjs --only <text>` runs just the routes whose name holds the text. `LT_LOGIN_N=8` adds sign-in timings (7s apart: sign-in is limited to 10 a minute per address).

For a before/after: bench with label `before`, make the change, `run.sh app <scale>` again (a new index migration lands on the same database), bench with label `after`. Two medians differ for real when their intervals do not overlap.

## Gotchas

- Bench one scale at a time with nothing else heavy running: the VPS has 2 cores, and a seed or a Gradle build next to a bench doubles the numbers.
- `Server-Timing` is in whole milliseconds, so routes under ~5ms read as noise; `wall` is kept in the raw file as a check.
- The writes (clock in, save, clock out) add one client and one session per run to the database. Over a few runs that is noise against the seeded rows.
- `run.sh app` needs `$LT_RUN/web/assets` to exist (Javalin refuses a missing static dir); `run.sh` makes it.

## Results (2026-11-05, master a6185a87 vs the V11 indexes and two query rewrites)

Server ms (`Server-Timing` db + app), median of 30 with its 95% bootstrap interval. The full table, every route at every scale, is `results/summary.csv`.

| Route | 1x before | 1000x before | 1000x after | Cause and fix |
| --- | --- | --- | --- | --- |
| Recent clients | 10.5 [9, 12] | 3681 [3491, 3935] | 7 [6, 9] | grouped every session by client to find the 20 newest; now walks sessions newest first on a `(created_at, id)` index |
| Client search | 16 [14, 22] | 2509 [2229, 2614] | 68 [66, 72] | `similarity(..) >= 0.2` can't use the trigram index and the phone prefix had none, so every search read every client; now `%` with the threshold set per transaction, and a phone prefix index |
| Client directory | 32 [29, 35] | 843 [761, 986] | 165 [151, 176] | sorted every client for the first 5000 by name; now a partial name index. The rest is the session count for those 5000 |
| Client history | 10 [9, 11] | 189 [177, 214] | 4 [4, 5] | walk-in product sales by client had no index |
| My history | 7 [7, 8] | 40 [36, 44] | 16 [13, 17] | shifts by user had no index |

Every other route stayed flat from 1x to 1000x. The VPS ran other work during these runs (load average 4-6 on 2 cores), so routes under ~20ms move by 2x between runs; compare a before and an after taken back to back.
