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
