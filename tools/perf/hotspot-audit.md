# Hotspot speed audit

Users are on slow phone hotspots, so every request in a row costs a round trip (about 300 ms) and every byte costs time (about 100 KB/s).
`hotspot.mjs` measures it. This file holds the ranked audit, the baseline and the rules the climb follows. The attempt log is `hotspot-decisions.tsv`.

## Harness (frozen)

`node tools/perf/hotspot.mjs --base URL --user U --pass P [--runs 5] [--no-save] [--only flow,prefix]`

| Setting | Value |
| --- | --- |
| Phone | Chromium 375x812, Asia/Manila |
| Network | 300 ms added latency, 800 kbit/s down, 300 kbit/s up. Chromium cannot drop packets, so loss is not simulated |
| Per flow | usable ms (tap until the screen shows content with no spinner), settled ms (last `/api` answer), `/api` calls, rounds (calls that had to wait for an earlier one), bytes over the wire |
| Flows | first visit (login page), sign-in to Today, reload signed in, open each screen once, back to Today, save a session, mark it Done |
| Noise | median of 5 runs; the min-max next to it is the noise band |

Prod runs go through the public address (proxy, TLS, compression as users get them) as the `probe` user (rights: view, edit, void on the probe branch; no pay screens). Local runs use `tools/verify/verify.sh up` and the `owner` login (all screens, 8-call Finance).
The save flow is run locally only: on prod it would add a session to the probe branch and shift the latency probe.

## Baseline (dc0a5d97, checked 2026-10-27, 5 runs each)

Prod (probe user):

| Flow | usable ms (min-max) | settled ms | calls | rounds | KB over the wire |
| --- | --- | --- | --- | --- | --- |
| first visit: login page | 2047 (1823-2130) | - | 0 | 0 | 92.1 |
| sign-in to Today | 1577 (1509-1625) | 1278 | 10 | 3 | 6.3 |
| reload (signed in) | 485 (371-896) | 1314 | 8 | 2 | 5.4 |
| open Expenses & pay | 531 (468-651) | 455 | 5 | 1 | 4.1 |
| open Change history | 58 (46-109) | 457 | 3 | 1 | 9.1 |
| open Stock / Clients / Reports / My shifts / Alerts | 349-413 | 333-384 | 1-2 | 1 | under 1 |
| open Relief duty | 396 (386-502) | 388 | 4 | 1 | 0.3 |

Local (owner):

| Flow | usable ms (min-max) | settled ms | calls | rounds |
| --- | --- | --- | --- | --- |
| sign-in to Today | 1475 (1395-2519) | 1345 | 10 | 3 |
| reload (signed in) | 336 (170-688) | 1149 | 8 | 2 |
| open Expenses & pay | 436 (398-541) | 692 | 8 | 1 |
| open Branches | 677 (657-768) | 671 | 3 | 2 |
| save a session | 864 (827-976) | 1081 | 3 | 3 |
| mark the session Done | - | 711 | 2 | 2 |

Reading it (checked): a screen that sends its calls together costs one round (about 350 ms) however many calls it has. Time is lost where calls wait for each other: sign-in (login, then four reads, then the day, Today's lists, shift, alerts, client list: 3 rounds after the login), a reload (2 rounds), Branches (2), a save (3 rounds: save, add the practitioner, re-read Today).

## Ranked report

Win is for a phone on the profile above. Effort: S under an hour, M a few hours, L a day. "Checked" means measured with the harness or read in code at dc0a5d97; "inferred" is reasoning not yet measured.

| # | Flow | Finding | Win | Effort |
| --- | --- | --- | --- | --- |
| 1 | Sign-in | 10 calls, 3 rounds after login (checked). One answer can carry all of it | about 0.5 s per sign-in, 10 calls to 2 | M |
| 2 | Every app open (reload, reopen) | 8 calls, 2 rounds (checked). One start request | settled 1149 to about 500 ms, 8 calls to 1 | M (same change as 1) |
| 3 | Finance open | 5 calls on prod, 8 with pay (checked). With a past date it is 2 rounds: the day first, then its lists (read in code) | 1 round for past days, 8 calls to 1 | M |
| 4 | Save a session | 3 calls in 3 rounds: save, add the practitioner, re-read Today (checked) | about 0.6 s per save: practitioners with the save, Today's lists in its answer | M |
| 5 | Mark Done | 2 calls in 2 rounds: mark, re-read Today (checked) | about 0.3 s per mark: the answer carries Today | S |
| 6 | Branches screen | 2 rounds: list, then staff and rates of the picked branch (checked) | 0.3 s per open | S |
| 7 | Reload shell | The service worker fetches the page and waits for it up to 3 s before using the saved copy (read in code). Whether this costs time on a slow link is not measured: Chromium's throttle may not reach service-worker fetches | up to 1 RTT per open (inferred) | S, measure first |
| 8 | Offline sync | Each held save replays as its own request (read in code), so a phone back online after a dozen saves pays a dozen round trips in sequence (inferred) | large after an outage, none otherwise | L |
| 9 | Client list | `GET /api/client-directory` at every sign-in. 2 KB on probe, grows with clients (checked small, real size unknown). Not on the usable path | bytes only | S, needs a size check on real data |
| 10 | First visit and deploys | 92 KB for the login page (88 KB script, 5 KB styles, brotli); every deploy is a new script name so a phone fetches it again (checked). Screens could load on demand | about 0.4 s on first visit and after deploys | M |
| 11 | Hashed files | Served with `max-age=0`, so a browser without the service worker revalidates each file per load (checked). The service worker serves them from its own copy | small | S |
| 12 | Other screens | Stock, Clients, Reports, Remittance, Relief, Users, Products, Alerts, Change history: 1-4 calls in one round (checked). Fewer calls would save only headers | under 50 ms each | skip |
| 13 | Payloads | Large JSON is gzipped (46 KB to 3.7 KB for Today on prod), scripts use brotli, idle is 0 calls (checked) | none | done |

Rules for the climb: one change per attempt; keep it only if the metric beats the noise band and the tests pass; no stale data beyond reopen/reload for other people's edits; offline must keep working; no prod database restart.
