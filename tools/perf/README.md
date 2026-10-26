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
