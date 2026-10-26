# Capacity results

Run 2026-10-26 and 27 at `fe587b99`, on the 2-core, 11 GB VPS (arm64) against a local verify stack on port 18185. The backend ran on the dev Postgres container `company-app-devdb` (Postgres defaults, `shared_buffers` 128 MB). Prod was never touched.

Reproduce with `tools/perf/load/seed.sh 40 8`, `node tools/perf/load/prep.mjs ...`, then `tools/perf/load/ladder.sh` (see each script's header).

## Read this first

The box was never quiet. Other agents' Gradle builds and tests ran the whole time. Every run below records `foreign`, the CPU used by processes that are neither the app, its database nor k6, as a share of one core (200% = both cores). It was 69% to 190%, so **the app and its database never had the machine to themselves.** Treat every capacity number here as a floor under contention, not a clean ceiling. `run.sh` waits up to `QUIET_WAIT` seconds for a quiet box and prints what it saw; it never saw one.

## What was tested

- Staff users: 40 cloned from the seeded `staff` login, 8 branches (5 users each), signed in once and clocked in. Tokens are reused across VUs because sign-in is limited to 10 per minute per IP. VU `i` uses user `i mod 40`.
- Each iteration: Today (dashboard, bookings, roster in one batch). 5% also save a session (a new client, then the session). 25% also open Finance (daily summary, expenses). Then think time, 4 to 8 s (mean 6 s), or none.
- Pass for a level: 5 minutes held, errors under 1%, p95 under Today 300 ms, save session 400 ms, Finance 800 ms. Sign-in under 2500 ms is checked apart (below). Steps marked 60 s are exploratory.
- p95 for Today and Finance is the slowest request of the batch. No run had a request error or a 429.

## Think-time runs (6 s mean), shipped pool of 3 connections

| VUs | hold | rps | Today p95 | save p95 | Finance p95 | foreign | pool waits (max) | verdict |
|---|---|---|---|---|---|---|---|---|
| 20 | 60 s | 9.7 | 193 | 76 | 88 | 190% | - | pass |
| 50 | 60 s | 24.8 | 119 | 99 | 59 | 168% | - | pass |
| 100 | 60 s | 47.0 | 233 | 171 | 183 | 168% | 1 | pass |
| 125 | 60 s | 59.3 | 177 | 98 | 102 | 130% | 2 | pass |
| 150 | 60 s | 65.0 | 3536 | 1002 | 1621 | 158% | 51 | fail |
| 200 | 60 s | 87.2 | 3101 | 1505 | 3076 | 91% | 232 | fail |
| 400 | 60 s | 76.1 | 14736 | 10528 | 12774 | 138% | 244 | fail |
| 125 | 5 min | 68.6 | 451 | 357 | 321 | 153% | 45 | fail |
| **100** | **5 min** | 56.1 | 68 | 66 | 46 | 69% | 1 | **pass** |

## Think-time runs, pool raised to 12 (local patch, not committed)

The patch reads `POOL_MAX` in `DatabaseConfig.kt`. The first 200 VU run (5451 ms) was a cold JVM and is not counted.

| VUs | hold | rps | Today p95 | save p95 | Finance p95 | foreign | pool waits (max) | verdict |
|---|---|---|---|---|---|---|---|---|
| 150 | 60 s | 71.6 | 138 | 88 | 106 | 82% | 0 | pass |
| 175 | 60 s | 82.7 | 170 | 135 | 123 | 84% | 0 | pass |
| 200 | 60 s | 92.9 | 822 | 768 | 560 | 79% | 5 | fail |
| 175 | 5 min | 96.0 | 450 | 498 | 297 | 74% | 51 | fail |
| **150** | **5 min** | 83.6 | 176 | 124 | 97 | 74% | 1 | **pass** |

## No think time, pool 12, 60 s

| VUs | rps | Today p95 | save p95 | Finance p95 | foreign | verdict |
|---|---|---|---|---|---|---|
| 5 | 122.5 | 212 | 173 | 101 | 107% | pass |
| 10 | 114.2 | 461 | 461 | 265 | 109% | fail |
| 20 | 122.4 | 901 | 895 | 673 | 100% | fail |

Throughput stays flat near 120 rps from 5 to 20 VUs, so more VUs only add queueing.

## The two numbers

- **(a) Realistic users, 6 s think:** 100 with the pool as shipped (3 connections), 150 with a pool of 12. Both held for 5 minutes under contention, so they are floors. For scale, the practice has about 20 users.
- **(b) No think time:** about 120 requests per second at the point where limits still hold (5 VUs). The shipped pool tops out near 90 rps (87 to 94 rps at 200 to 400 VUs, every limit blown).

## What saturated (explain the number)

- **CPU, checked.** In the 120 rps runs the app, database and k6 used 0.43 + 0.42 + 0.05 = 0.9 core while foreign processes used about 1.05 cores. The box was 99% busy (`/proc/stat`). Per request CPU, from `/proc` and the container's cgroup counters: app 2.7 to 3.6 ms, database 2.3 to 3.6 ms, k6 0.5 ms. That is about 7 ms per request.
- **Inferred, not run.** With both cores free, 2000 ms / about 6.5 ms (without k6) gives roughly 300 rps, about 500 users at this mix. Nobody measured it. A rerun on an idle box would.
- **Connection pool, checked at 3 connections.** `DatabaseConfig.kt` caps Hikari at 3 (`MAX_POOL_SIZE`). At the shipped size, 45 to 244 requests were waiting for a connection while app plus database used 0.5 core. With 12 connections the same mix passed at 150 VUs with 0 to 1 waiting. Foreign load differed between the runs (69% to 153%), so the 100 to 150 gain is directional, not exact.
- **Sign-in, checked.** One login costs about 370 ms of app CPU (bcrypt cost 12, 8 logins measured) and took 0.6 to 1.6 s under contention. That equals about 50 ordinary requests. The probe in earlier smoke runs saw sign-in p95 2.2 to 4.9 s while the box was saturated, which is over the 2500 ms limit. It was not measured at the edge, so sign-in stayed out of the pass rule.
- **Login rate limit, checked in logs.** All 5 login attempts in prod's `app.log` come from one private address, 10.0.2.2. The limit is 10 per IP per minute with no forwarded-header handling in the code (grep). If that address is the proxy, every user shares 10 sign-ins per minute (inferred, not tested).

## Cheap changes that would raise it (unbuilt)

1. Raise `MAX_POOL_SIZE` from 3 to about 12 (and `MIN_IDLE` to match or stay at 3). Rests on the local runs above (checked on a contended box). Check that prod Postgres `max_connections` allows it first (unchecked).
2. Key the login limit on the real client address, or raise it, so a morning rush of sign-ins is not refused (rests on the prod log and a code grep, inferred). Lowering bcrypt cost would cut about 370 ms of CPU per login, but weakens password hashing (a guess at the size of the gain, and the user's call).

## Unchecked

- Any number on an idle box. Rerun `ladder.sh` when other builds are done.
- Dev Postgres is not prod's tuned Postgres 18 (prod has `pg_stat_statements` and a 500 ms slow log). The difference was not measured.
- Sign-in under load at the edge.
- Dashboard size grows as sessions accumulate (about 55 to 60 per branch per 5-minute run here). A day with hundreds of sessions at one branch would cost more.
