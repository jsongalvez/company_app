# Latency probe

Every 5 minutes `probe.sh` signs in as the `probe` user on the `probe` branch, loads Today, saves one session and voids it, and loads Finance.
Each step writes a row to `~/logs/company_app/probe.jsonl` with the client time and the server's `Server-Timing` split (database ms, code ms).
`check-latency.py` runs inside the one-minute alert timer (`company-app-alert.sh latency`) and sends a phone alert through ntfy.

| Rule | Value |
| --- | --- |
| Limits | `limits.json`: sign-in 2500ms, Today 300ms, saving a session 400ms, Finance 800ms. Guesses from a few prod requests; reset them to twice a week's p95 once the probe rows exist |
| Alert | the route is over its limit in 2 probe runs in a row, or real traffic in the last hour has 20+ requests with a p95 over it. One alert per breach |
| Recovery | 2 good probe runs under the limit. After a breach seen in real traffic, 20 fresh real requests under the limit too |
| Quiet | failed probe runs (app down) send nothing: the health alert covers that |

The alert ends with `perf-slow <traceId>`: the request's log lines, its slow statements, and the three statements that added the most database time around it (from the probe's `pg_stat_statements` snapshots). When the time was in code and the route is still slow, it starts 60s of JFR on the app.

`install-probe.sh` puts the scripts in `~/.local/share/company-app-perf`, enables `company-app-probe.timer` and adds `check_latency` to `~/bin/company-app-alert.sh`. `seed-probe.sql` creates the probe user and branch (it holds only view, edit and void rights on the probe branch). Settings live in `~/.config/company-app-probe.env`: `PROBE_USER`, `PROBE_PASS`, `PROBE_DB_CONTAINER`.
Test: `tools/perf/test/alert.test.sh`.

The prod database container does not load `pg_stat_statements` (checked 2026-10-27: `shared_preload_libraries` is empty), so on prod `perf-slow` prints the request's `slow_statement` lines (over 200ms) instead of the top-3 table. Loading it needs `shared_preload_libraries=pg_stat_statements` and a database restart, left for the owner.

# Calls per flow

`calls.mjs` drives the phone web app in a real browser (375px, added latency) and counts the `/api` calls each flow makes: sign-in to the first Today, open Finance, Today again after 20s, a Today, Finance, Today round trip, tab hidden then visible, window blur then focus, save a session, mark it Done, reload, idle. It prints calls, rounds and the endpoint list per flow.

```
node tools/perf/calls.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass --runs 3
```

`CALLS_USER` and `CALLS_PASS` in the environment do the same and keep a password off the command line.

`tools/verify/verify.sh up` gives the local app. The save flow writes one session for a client named "Probe" (made on first use) and marks it Done: point it at a test or probe account, or pass `--no-save`.

# Hotspot harness

`hotspot.mjs` is the frozen measure for slow phone hotspots: a 375px phone at 300 ms latency, 800 kbit/s down and 300 kbit/s up. For each flow it prints the time until the screen is usable, the time until the last answer, `/api` calls, rounds and bytes. It takes the median of `--runs` runs and shows the min-max as the noise band.

```
node tools/perf/hotspot.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass --runs 5
HOTSPOT_USER=probe HOTSPOT_PASS=... node tools/perf/hotspot.mjs --base https://<public address> --runs 5 --no-save
```

`hotspot-audit.md` holds the ranked audit and the baseline; `hotspot-decisions.tsv` logs each attempt (kept or reverted). Do not change the profile or the flows without saying so in the PR: the numbers stop being comparable.

# Day volume and fixtures

`fill-day.mjs` fills today's business day at one branch through the API (sessions, Done marks, product sales) to measure Today as the day fills. It refuses an https base unless `FILL_ALLOW_REMOTE=1`. `clean-fixtures.mjs` lists the `Probe… Client` clients perf runs left on a server, proves each is a fixture (every session on the probe branch, not the live probe's client) and erases them with `--apply`. Results are in [vitals.md](vitals.md).

```
node tools/perf/fill-day.mjs --base http://127.0.0.1:18181 --sessions 50
PROBE_USER=probe PROBE_PASS=... node tools/perf/clean-fixtures.mjs --base https://<public address> [--apply]
```

# Web vitals

`vitals.mjs` gives LCP, INP, CLS, FCP, TTFB, TBT, script and style bytes and cache hits for a cold and a warm open, through `shaper.mjs` (a TCP relay that slows every byte, handshakes and service-worker fetches included). See [vitals.md](vitals.md) for the profile and the baseline.
