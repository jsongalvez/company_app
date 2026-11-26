# Offline acceptance record

The bar (`AGENTS.md` Goal): staff can do a full day of work on an iPhone, an Android phone or a browser, whether the wifi is bad or there is no signal at all. This file lists what has been checked against that bar and what has not, as of Nov 26. Built in [plan.md](plan.md) Revisions 7. The repo is public, so this file quotes no one.

Evidence words, as in the plan: checked (ran it, or read the CI run), inferred, or not checked.

## Checked

| What | Evidence | How it was checked |
|---|---|---|
| A replayed save lands once: a resend with the same `X-Op-Id` gets the first answer | `backend/src/test/kotlin/com/companyb/companyapp/audit/OpLogTest.kt`, `backend/src/test/kotlin/com/companyb/companyapp/integration/SessionVoidReplayPostgresTest.kt` | backend tests, CI `quality.yml` |
| A save counts at the time it was made (`X-Occurred-At`): within 48 h it lands on the day it was made, flagged for review; an older one needs the right to change a closed day, and a remitted day takes none | `backend/src/test/kotlin/com/companyb/companyapp/branchday/EventTimePostgresTest.kt` | backend tests, CI `quality.yml` |
| Queued saves can still be sent after the sign-in has expired: a replay token buys a new sign-in for 7 days (`/auth/refresh`), and nothing after that or after signing out | `backend/src/test/kotlin/com/companyb/companyapp/identity/ReplayTokenPostgresTest.kt` | backend tests, CI `quality.yml` |
| The queue's pure logic: a save replayed after the day turned over carries a reason, and every status gets one verdict | `web/tests/unit/replay.test.ts`, `web/tests/unit/outbox-classify.test.ts` | node unit tests, the "Unit tests for the save queue's pure logic" step in each `browser.yml` job |
| The queue's design: no save vanishes from Today, none the person made in a sensible order is refused, the server ends on the last choice, late work isn't refused inside the window | `tools/tla/outbox/Outbox.tla` and its configs; each counterexample it found is a browser test in `web/tests/outbox-model.e2e.ts` | TLC, `.github/workflows/tla.yml`: `tools/tla/outbox/run.sh` (every `fixed-*`, `noCapability-*`, `eventTime-*` config passes, every `today-*`, `partial-*` one fails) and `ablate.sh` (each of the 10 fixes is needed), on each change to the model, `outbox*`, `cache*`, `disk*`, `today*`, `api/client.ts`, `Today.svelte`, `OpLog.kt` or `OpTime.kt`. The model was last reread against the code at `4999e610` |
| A full day with no signal, and one on bad wifi, ends with the same server totals as the same day worked online | `web/tests/offline-day.e2e.ts`, seed `tools/seed/offline-day.sql` | `browser.yml` in Chromium (service worker on), WebKit and Firefox |
| A shift worked offline on a past day, with the app closed and opened in between, lands on the day it was worked | `web/tests/offline-hours.e2e.ts` | `browser.yml` in Chromium, WebKit and Firefox |
| Offline saves, marks, clashes and search | `web/tests/outbox.e2e.ts`, `web/tests/offline-search.e2e.ts` | `browser.yml`, Chromium only |
| In iPhone Safari's engine the app asks to keep its data (`navigator.storage.persist`, `web/src/lib/disk.ts`) and shows how to add it to the Home Screen | `web/tests/iphone.e2e.ts` | `browser.yml` WebKit job, with WebKit playing an iPhone; skipped in Firefox (no mobile emulation) |
| Four phones on a laptop's hotspot with no internet see each other's unsent work through the site hub; the hub is killed and refilled by the phones; once the signal is back the cloud holds what the same work done online holds | `web/tests/site-hub.e2e.ts`, relay `hub/` | `browser.yml` "site hub" job: one phone per engine (Chromium, WebKit, Firefox), relay in a container |

What the browser runs prove has limits:

- WebKit and Firefox run with service workers off. A worker's requests skip the tests' network cuts there, so an added header makes the engine block workers (`web/e2e.config.ts`, commit `21f17188`). Opening the app from the worker's cache with no signal is checked in Chromium only.
- The site hub job blocks service workers in every engine (`serviceWorkers: "block"` in `web/tests/site-hub.e2e.ts`). Each phone is a desktop browser context that reaches the hub through a proxy, not a phone on a hotspot. Since `95b2ef1a`, each phone waits for a tap on Find site hub before it asks for the local network, in every engine.
- The browsers are Playwright's builds of each engine on Linux, not Safari on iOS or Chrome on Android.
- `effb28f2` lets the test sign-in open the page once more when Firefox fails to open it right after the state is cleared. That is a retry in the test harness, not a fix in the app.
- Last green `browser.yml` run with every job above: `95b2ef1a` and `d31d2514` (Nov 21).

## Not checked

| What | Why it matters | What would check it |
|---|---|---|
| A real iPhone and a real Android phone, through a full working day in the field | Emulation can't show real radios, sleep, the app killed in the background, or a person's taps | A field trial on the staff's own phones, with a checklist and the server totals compared afterwards |
| Safari on iOS keeping the app's IndexedDB and queue: after 7 days unused, with and without the Home Screen, and under low storage | Safari may clear a site's data. A queue that is cleared loses work, which the Avoid line forbids | Real iPhones left for more than 7 days in each state, then opened with no signal |
| Chrome on Android keeping storage under pressure, and whether `persist()` is granted | An eviction loses queued work the same way | Real Android phones with the disk nearly full |
| Service workers in Safari and Firefox with no signal: the app opening from its cache | Every non-Chromium browser run has workers off (above) | A browser test that cuts the network another way, or a real device |
| Several real phones on one site hub with no internet: a real hotspot, local-network permission prompts, phones sleeping and waking | The CI job uses proxies and desktop contexts | A trial with a laptop and three or more phones away from any signal |
| Many phones' late work landing at once | Not modelled (one person, one phone in the model), not load-tested | A model or load test with several phones replaying together |
