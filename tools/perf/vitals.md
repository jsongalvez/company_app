# Web vitals on a slow hotspot

`vitals.mjs` measures what web teams report for a page: Core Web Vitals (LCP, INP, CLS), FCP, TTFB, TBT (the lab stand-in for INP), JS and CSS bytes over the wire, and how many app files came from a cache. It runs a signed-in phone, which Lighthouse cannot do without scripting.

```
node tools/perf/vitals.mjs --base http://127.0.0.1:18181 --runs 5
HOTSPOT_USER=probe HOTSPOT_PASS=... node tools/perf/vitals.mjs --base https://api.kiel.party --runs 5
```

| Setting | Value |
| --- | --- |
| Phone | Chromium 375x812, CPU slowed 4x (Lighthouse mobile's setting; on this ARM VPS core it is a harsher phone than Lighthouse's, so TBT reads high) |
| Network | 300 ms round trip, 800 kbit/s down, 300 kbit/s up, applied by `shaper.mjs` to every byte |
| Loads | cold login (nothing saved), cold Today (signed in, no saved app files), warm Today (the everyday open: service worker and caches warm) |
| INP | three scripted taps on warm Today (Menu, Expenses & pay, back to Sessions): the slowest tap |
| Noise | median of 5, min-max beside it |

Good thresholds (web.dev, at p75 of real visits): LCP 2.5 s, INP 200 ms, CLS 0.1, FCP 1.8 s, TTFB 0.8 s. These are lab runs on one profile, so they stand in for p75 rather than being it.

`shaper.mjs` is a TCP relay that delays and paces bytes like netem. Chromium's own network emulation (what `hotspot.mjs` uses by default) adds latency per request only. It skips connection set-up, TLS and the service worker's fetches, so a warm open looked one round trip faster than it is (checked: TTFB 16 ms under emulation, 338 ms through the shaper). `hotspot.mjs --shaper` runs the flows through it, and `--dist DIR` serves the page from a local build so a web change can be A/B tested against the live API.

## Baseline (d32b9afe, 2026-10-27, 5 runs)

Prod, probe user, through the public address (HTTP/2, TLS, gzip/brotli):

| Load | TTFB | FCP | LCP | usable | TBT | CLS | INP | JS KB | CSS KB | files from cache |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cold login | 1111 (1096-1164) | 2668 (2628-3000) | 2668 | 2695 | 0 | 0.000 | - | 86.8 | 5.0 | 0 of 2 |
| cold Today | 1111 (1095-1119) | 2708 (2680-2868) | 2708 | 3233 (3191-3669) | 86 | 0.003 | - | 86.8 | 5.0 | 0 of 2 |
| warm Today | 323 (317-332) | 664 (616-688) | 664 | 1251 (1030-1322) | 172 (1-419) | 0.003 | 104 (48-176) | 0 | 0 | 2 of 2 |

Local verify stack, owner:

| Load | TTFB | FCP | LCP | usable | TBT | CLS | INP |
| --- | --- | --- | --- | --- | --- | --- | --- |
| cold login | 643 | 2324 (2168-2688) | 2324 | 2348 | 0 | 0.000 | - |
| cold Today | 640 | 2252 | 2344 | 2713 | 0 | 0.002 | - |
| warm Today | 343 (335-437) | 692 | 792 | 1192 (1119-1387) | 0 | 0.002 | 56 (32-96) |

Reading it (checked unless marked):
- Cold TTFB on prod is about 3.7 round trips: TCP, TLS, the request, and the server's answer. A local plain-HTTP stack pays 2.
- Cold FCP is mostly the 87 KB script at 100 KB/s (about 0.9 s) after TTFB. LCP equals FCP: the largest paint is the first one.
- Warm TTFB is one full round trip. The service worker waits for the network before using its saved page (`sw.js`, up to 3 s).
- Warm usable trails FCP by about 600 ms on prod. The probe user is not clocked in, and the Clock in button waits for `/api/start` (inferred from `Shell.svelte:239`, `app.shiftKnown`).
- CLS and INP are good. TBT is small next to network time.

## After #985-#991 (abccf22e, 2026-10-28, prod, probe user, 5 runs)

| Load | metric | before (d32b9afe) | after (abccf22e) |
| --- | --- | --- | --- |
| warm Today | TTFB | 323 | 7 |
| warm Today | FCP = LCP | 664 | 360 |
| warm Today | usable | 1251 (1030-1322) | 1050 (836-1208) |
| warm Today | INP | 104 | 64 |
| no-SW Today | asset requests | two 304s | none (immutable) |
| cold login | FCP = LCP | 2668 | 2684 (unchanged: same 87 KB script) |
| all | CLS | 0.000-0.003 | 0.000-0.003 |

TBT and cold Today usable rose on prod between the two runs (cold Today TBT 86 to 121-801). The d32b9afe build, served against the same prod API at the same time, shows the same rise (cold Today TBT 560-686, checked), so it is not these changes. Inferred cause: Today renders every session of the day, and the probe branch holds about 256 by night (the probe saves and voids one every 5 minutes). Real branches have far fewer.

Flows on prod (`hotspot.mjs`, the save flow with a fresh client per run so the first-visit price applies; median of 5):

| Flow | before | after |
| --- | --- | --- |
| sign-in to Today | 903 usable, 2 calls | 881, 2 calls |
| reload | 348 usable, 621 settled | 301, 546 settled |
| Expenses & pay | 484, 1 batch call | 526 (502-556), 5 parallel GETs, bands overlap |
| save a session | not measured live | 924 usable, 817 settled, 2 calls |
| mark the session Done | not measured live (local 702 settled, 2 calls) | 401 settled, 1 call |
| Branches (local, owner) | 668 usable, 2 rounds | 364, 1 round |

Dropped, with the reason:
- Clock in from the saved shift: no gain beyond noise in a one-stack A/B (warm usable 438 vs 357, bands 335-561 and 289-652).
- Offline saves sent together: replay runs in the background and blocks nobody. Order and idempotency are pinned by `tools/tla/outbox`. Not measured.
- Client list size: 260 bytes per client before compression, 9 clients on prod (563 B brotli). A delta sync pays off only at thousands of clients (inferred).
- Code splitting: a vendor chunk would save about 20 KB gzip per deploy, and #991 already moves the post-deploy download off the critical path. Lazy pages would cut the first-visit login script (inferred, about 0.3 s), but would also need the service worker to precache chunks.
- HTTP/2, HTTP/3, brotli, preconnect: already in place or not applicable (checked). h2 with `alt-svc: h3`, brotli on assets and API (`/api/start` 94 KB raw to 6.3 KB), all requests same-origin.
- 150-user capacity rerun: the paths it loads are unchanged. The batch A/B (`load/batch-ab.js`) measured the one server change: +13% screens/s at saturation.

## Leftovers (2026-10-28, local verify stack unless marked)

Local runs share the VPS's 2 cores with prod, and a root `yes` process at nice 19 kept one core busy during these runs (seen in `top`, owner unknown). They slow prod and blur prod numbers (a Finance report at 09:39 lined up with them). Run them with `nice -n 19`, and take no prod numbers while one runs.

**First visit to the sign-in page (#995).** The signed-in app is now its own file. Signed-out opens fetch it after the sign-in screen is up; signed-in opens preload it beside the page script. The service worker saves the files the page script names, so offline still works.

| Load | metric | before (894bf697) | after (#995) |
| --- | --- | --- | --- |
| cold login | FCP = LCP | 2256 (2200-2396) | 1768 (1632-2224) |
| cold Today | usable | 2631 (2624-2945) | 2478 (2421-3836) |
| warm Today | FCP | 264 (196-488) | 272 (256-616) |

Entry script 90.7 KB to 37.8 KB gzip. Prod cold login read 3144 (2800-3676) before and 2952 (2204-3376) after. That run had load average 4 on the 2 cores and 2 of 5 runs failed, so the prod pair is inconclusive.

**Today as the day fills.** `fill-day.mjs` adds sessions (two in three marked Done) and one product sale per three sessions through the API. Server time stays flat. The browser's work grows with the rows.

| sessions | today-screen server | answer (brotli) | main thread busy, 4x CPU | longest task | warm Today TBT (vitals) |
| --- | --- | --- | --- | --- | --- |
| 0-3 | 19 ms | 0.8 KB | 247 (186-358) | 71 | 0 |
| 50 | 16 ms | 3.5 KB | 323 (264-356) | 128 | 82 (0-179) |
| 100 | 14 ms | 6.4 KB | - | - | 136 (13-314) |
| 200 | 15 ms | 12.0 KB | 790 (632-1149) | 230 | 549 (18-607) |

About 2.7 ms of main thread per row at 4x CPU, spent in Svelte and the DOM. No row does needless work: the formatters are made once and the outbox is empty. Real branches log far fewer sessions a day than 200 (guessed: prod data reads were not available). The prod rise [Perf] saw is the probe's own branch: the probe saves and voids a session every 5 minutes, about 288 rows by night. Tried and dropped, each with no gain beyond noise at 200 sessions: one pass for the held marks plus a shared date formatter (busy 1024 vs 790); rows added a screenful per frame (busy 1077, longest task 217 vs 230). The list is oldest first, so showing only the first rows would hide the ones staff mark next.

**Saving a session (#996).** Illnesses now go in the save. Timed in the page from the tap to the dialog closing, new client, 300 ms latency, median of 5:

| Save | before | after |
| --- | --- | --- |
| three illnesses (2 ticked, 1 typed) | 1425 (1361-1584), 4 writes | 377 (360-392), 1 write |
| no illnesses | 409 (375-454) | 403 (368-422) |

The live 924 ms above came from `hotspot.mjs`, whose `waitFor(hidden)` polls every 500 ms after the first second. The same local flow read 857 that way and 409 timed in the page (checked locally; that the live number inflated the same way is inferred).

**Fixtures on prod.** The prod save runs above left 6 clients named `ProbeNNNNNN Client`. A probe reset left one more `Probe Client` with 110 voided sessions. `clean-fixtures.mjs` proves each one: the name, and every session on the probe branch. Client history is not filtered by the reader, so the probe sees every branch. It skips the probe's live client and with `--apply` erases them through `POST /api/clients/{id}/anonymize`. The dry run on prod lists those 7 (checked). Not applied: the agent's permission check refused the prod write.
