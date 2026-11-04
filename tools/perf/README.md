# Latency probe

Every 5 minutes `probe.sh` signs in as the `probe` user on the `probe` branch, loads Today, saves one session and voids it, and loads Finance.
Each step writes a row to `~/logs/company_app/probe.jsonl` with the client time and the server's `Server-Timing` split (database ms, code ms). Sign-in is anonymous and gets no `Server-Timing`, so its rows carry null splits and the alert uses its client time.
`check-latency.py` runs inside the one-minute alert timer (`company-app-alert.sh latency`) and sends a phone alert through ntfy.

| Rule | Value |
| --- | --- |
| Limits | `limits.json`: sign-in 2500ms, Today 300ms, saving a session 400ms, Finance 800ms. Guesses from a few prod requests; reset them to twice a week's p95 once the probe rows exist |
| Alert | the route is over its limit in 2 probe runs in a row, or real traffic in the last hour has 20+ requests with a p95 over it. One alert per breach |
| Recovery | 2 good probe runs under the limit. After a breach seen in real traffic, 20 fresh real requests under the limit too |
| Quiet | failed probe runs (app down) send nothing: the health alert covers that |

The alert ends with `perf-slow <traceId>`: the request's log lines, its slow statements, and the three statements that added the most database time around it (from the probe's `pg_stat_statements` snapshots). When the time was in code and the route is still slow, it starts 60s of JFR on the app.

`install-probe.sh` puts the scripts in `~/.local/share/company-app-perf`, enables `company-app-probe.timer` and adds `check_latency` to `~/bin/company-app-alert.sh`. `seed-probe.sh` runs `seed-probe.sql` on the prod database: the probe user and its branch (it holds only view, edit and void rights there). Rerun it after any database reset; a second run changes nothing. The branch is hidden (`branch.hidden`, V5): branch lists, the all-branches export and a global viewer's audit leave it out. The probe user and its "Probe Client" still show in Users and Clients. Settings live in `~/.config/company-app-probe.env`: `PROBE_USER`, `PROBE_PASS`, `PROBE_DB_CONTAINER`.
Test: `tools/perf/test/alert.test.sh`.

The prod database container does not load `pg_stat_statements` (checked 2026-10-27: `shared_preload_libraries` is empty), so on prod `perf-slow` prints the request's `slow_statement` lines (over 200ms) instead of the top-3 table. Loading it needs `shared_preload_libraries=pg_stat_statements` and a database restart, left for the owner.

# Calls per flow

`calls.mjs` drives the phone web app in a real browser (375px, added latency) and counts the `/api` calls each flow makes: sign-in to the first Today, open Finance, Today again after 20s, a Today, Finance, Today round trip, tab hidden then visible, window blur then focus, save a session, mark it Done, reload, idle. It prints calls, rounds and the endpoint list per flow.

```
node tools/perf/calls.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass --runs 3
```

`CALLS_USER` and `CALLS_PASS` in the environment do the same and keep a password off the command line.

`tools/verify/verify.sh up` gives the local app. The save flow writes one session for a client named "Probe" (made on first use) and marks it Done: point it at a test or probe account, or pass `--no-save`.

# Server time per route

`route-bench.mjs` signs in once and sends `--n` requests to each main read route, reading the app's own `Server-Timing` (db and app ms), so the network and the browser are out of the number. For an A/B, start a copy of the old commit on 18282 (see `verify.sh at`) and alternate runs against both ports; single runs on the VPS move 2-3x with its other load.

```
node tools/perf/route-bench.mjs --base http://127.0.0.1:18181 --n 80
```

# Main thread per tap

`tap-profile.mjs` signs in on a phone-sized page with Chromium's CPU slowdown (default 4x, no network shaping) and, for each of the menu, a screen, the menu again and Sessions, prints the tap's Event Timing duration and the functions with the most self time, mapped to `web/src` through the build's sourcemaps. Native `getBoundingClientRect`, `elementsFromPoint` and aria lookups are Playwright's own click work.

```
node tools/perf/tap-profile.mjs --base http://127.0.0.1:18181 --runs 5
```

# Hotspot harness

`hotspot.mjs` is the frozen measure for slow phone hotspots: a 375px phone at 300 ms latency, 800 kbit/s down and 300 kbit/s up. For each flow it prints the time until the screen is usable, the time until the last answer, `/api` calls, rounds and bytes. It takes the median of `--runs` runs and shows the min-max as the noise band.

```
node tools/perf/hotspot.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass --runs 5
HOTSPOT_USER=probe HOTSPOT_PASS=... node tools/perf/hotspot.mjs --base https://<public address> --runs 5 --no-save
```

From Nov 4 "save a session" is usable once the dialog has closed and the new row is on Today (the save no longer waits for the server). `hotspot-audit.md` holds the ranked audit and the baseline; its "save a session" usable times from before Nov 4 include up to ~400ms of Playwright polling lag (the page had closed the dialog at the answer). Measure on a stack started without `SLOW_STATEMENT_MS=0`: logging every statement slows each request. `hotspot-decisions.tsv` logs each attempt (kept or reverted), see "Decision log" below. Do not change the profile or the flows without saying so in the PR: the numbers stop being comparable.

# Day volume and fixtures

`fill-day.mjs` fills today's business day at one branch through the API (sessions, Done marks, product sales) to measure Today as the day fills. It refuses an https base unless `FILL_ALLOW_REMOTE=1`. `clean-fixtures.mjs` lists the `Probe… Client` clients perf runs left on a server, proves each is a fixture (every session on the probe branch, not the live probe's client) and erases them with `--apply`. Results are in [vitals.md](vitals.md).

```
node tools/perf/fill-day.mjs --base http://127.0.0.1:18181 --sessions 50
PROBE_USER=probe PROBE_PASS=... node tools/perf/clean-fixtures.mjs --base https://<public address> [--apply]
```

# Web vitals

`vitals.mjs` gives LCP, INP, CLS, FCP, TTFB, TBT, script and style bytes and cache hits for a cold and a warm open, through `shaper.mjs` (a TCP relay that slows every byte, handshakes and service-worker fetches included). See [vitals.md](vitals.md) for the profile and the baseline.

# Statements per request

`SLOW_STATEMENT_MS=0 tools/verify/verify.sh up` logs every SQL statement with its trace id. Drive the app (`calls.mjs`, `hotspot.mjs`), then `python3 tools/perf/sql-trace.py /tmp/verify-run-18181/backend.log [--route clock-in]` lists, per route, the statements of its biggest request and how often each ran, so a lookup repeated per row (`5x`) stands out.

# Decision log

`hotspot-decisions.tsv` has one row per perf attempt: `id hypothesis change before after delta tests verdict note`. `check-decisions.py` runs in the quality workflow (`tools/perf/test/decisions.test.sh`) and refuses:

- a row with no measured before or after;
- a kept change whose `tests` names no guard that failed before the change (write `fails before` or `fails on the old code`), unless it says `no guard: <reason>`;
- a plain `kept` with no prod number. Write `kept (pending prod)` at push time, then add a `<id>p` row with the prod run after the deploy.

`python3 tools/perf/check-decisions.py tools/perf/hotspot-decisions.tsv --due` lists the kept rows still waiting for a prod run. Most can be measured with the probe account:

```
set -a; . ~/.config/company-app-probe.env; set +a
HOTSPOT_USER="$PROBE_USER" HOTSPOT_PASS="$PROBE_PASS" node tools/perf/hotspot.mjs --base https://app.kiel.party --runs 5
```

The save flow saves under the live probe's "Probe Client" on the hidden probe branch, so `clean-fixtures.mjs` finds nothing to erase afterwards.

# Past mistakes the tools now catch

| Mistake | What catches it now |
| --- | --- |
| Save "usable" read ~400ms late: the harness waited with `locator.waitFor`, which backs off to 500ms polls | `hotspot.mjs` polls at 50ms; compare a harness time with an in-page `performance.now()` mark before trusting a new flow |
| Measuring with `SLOW_STATEMENT_MS=0` on, which logs every statement and slows each request | the README rule above; take timings on a stack started without it |
| #986 (service worker) reverted as the cause of the refresh flash; builds from before it flashed too | bisect with `tools/verify/film.mjs` blank-frame counts across builds before reverting a suspect |
| One batch request (A2) looked 43% faster locally and was within noise on the prod profile; it merged parallel reads, not dependent rounds | count rounds, not calls (`calls.mjs`, `hotspot.mjs`); a `<id>p` prod row before calling it kept |
| A scan ranked clock-out's cache drop as the top bug; `git log -S` showed it was intended (88793742) | `find-state-machines` step 3 asks for the commit that made the state before calling it a bug |
