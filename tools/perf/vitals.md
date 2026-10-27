# Web vitals on a slow hotspot

`vitals.mjs` measures what web teams report for a page: Core Web Vitals (LCP, INP, CLS), FCP, TTFB, TBT (the lab stand-in for INP), JS and CSS bytes over the wire, and how many app files came from a cache. It runs a signed-in phone, which Lighthouse cannot do without scripting.

```
node tools/perf/vitals.mjs --base http://127.0.0.1:18181 --runs 5
HOTSPOT_USER=probe HOTSPOT_PASS=... node tools/perf/vitals.mjs --base https://api.140.245.56.102.nip.io --runs 5
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
