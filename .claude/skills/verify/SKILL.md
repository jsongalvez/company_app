---
name: verify
description: "Run CompanyApp (Kotlin backend + Svelte web app) locally on its own port and database, sign in as a seeded user, drive a page like a user in a real browser, and save desktop and 375px phone screenshots as proof. Use to confirm a UI or API change works in the real app, to reproduce a bug report on a page (#/today, #/audit, ...), or before saying 'done' on web changes."
---

# Verify CompanyApp

You are driving a web app: Svelte 5 single page (hash routes like `#/today`) served by the Kotlin backend on one port.
Everything is one script: `tools/verify/verify.sh` (run it from any checkout or worktree of this repo).
Another instance (the dev server on `:18080`, or another agent's run) may be up. This skill never touches it:
it uses its own port (`18181`), its own Postgres database (`verify_18181`), its own web build, its own run dir.

## Launch

```bash
tools/verify/verify.sh up        # about 40s warm, longer on a cold Gradle cache
```

What it does: builds `web/` into `/tmp/verify-run-18181/dist` (runs `npm ci` once if `web/node_modules` is missing),
creates an empty database `verify_18181` on the dev Postgres (credentials read from the main checkout's `.env`, never printed),
starts `./gradlew :backend:run` in its own process group (Flyway migrates, `DevMain` seeds), then waits until
`/health` is UP **and** `POST /auth/login` as `owner` returns 200. Health alone is not ready: it answers before the seeder runs.
Needs: JDK 21, Node 24+ (it finds `~/.local/node/bin/node` if `node` on PATH is older), Postgres reachable via `.env` (`DB_HOST`, `DB_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`), a Playwright Chromium in `~/.cache/ms-playwright` (`cd web && npx @e2e-dev/web install chromium` if missing).
Another stack in parallel: `VERIFY_PORT=18182 tools/verify/verify.sh up` (run dir and DB follow the port).

Seeded logins (scratch DB only, from the same env the CI browser job uses):
`owner` / `owner-dev-pass` (GLOBAL owner, display name "Dev owner"), `staff` / `staff-dev-pass` (scoped to the K6 Fixture Branch),
`relief` / `relief-dev-pass`, and `dev` (SUPERUSER, for View as). Override with `VERIFY_USER` / `VERIFY_PASS`.
The scratch DB has one branch (K6 Fixture Branch, id `00000000-0000-4000-8000-000000000001`), no clients, no sessions, no past days.

## Doctor

```bash
tools/verify/verify.sh doctor    # read-only; exit 0 = worth driving
```

Prints: pid file present, who owns the port, `/health`, login works, worktree commit and dirty count, web build time.
Run it first whenever a shot looks wrong. A stale web build is the usual cause: `down` then `up` rebuilds it (the build is made at `up`, not at `shot`).
If you changed Kotlin, restart too.

## Drive

```bash
tools/verify/verify.sh shot '#/today'            # saves desktop (1400x860) and phone (375x812 @2x) full-page PNGs
tools/verify/verify.sh shot '#/audit' audit-fix  # optional second argument names the files
```

`tools/verify/drive.mjs` (Playwright, taken from `web/node_modules`) does, per viewport, in a fresh browser context:
signs in through the real login form (`Username`, `Password`, `Sign in`), waits for the app to boot,
clocks in at the current branch like a real shift (`VERIFY_CLOCKIN=0` skips), opens the route (reloading after the clock-in so data is fresh), waits until no "Loading" text is left,
screenshots. It prints console errors, page errors, any `/api/` response >= 400 and, on the phone, horizontal overflow, as `PROBLEM` lines
and exits 1 if there are any. Things seen before the route opens (sign-in, clock-in) print as `NOTE` and do not fail the run.
A run with `PROBLEM` lines is a finding, not a flaky helper: read the screenshot and the line before dismissing it.

For interactions beyond opening a page (tap a button, fill a form, check a result), write a small Playwright script next to `drive.mjs`
using the same sign-in block, with stable handles: roles and labels (`getByRole("button", { name: "New session" })`,
`getByLabel("Username")`), route paths. Prefer extending the existing browser tests in `web/tests/*.e2e.ts` when the check should stay
(run only the test file you touched locally: `cd web && APP_URL=http://127.0.0.1:18181 npx e2e run tests/<file>.e2e.ts`; the full suite runs on GitHub only).
The phone menu is collapsed: `nav[aria-label="Main"]` exists but is hidden, so use a CSS locator, not `getByRole`, to wait for it.

## Film (what the screen does between two states)

```bash
tools/verify/verify.sh film '#/today'                          # film 4 reloads (VERIFY_FILM_RUNS=n for more)
tools/verify/verify.sh film '#/today' 'Clock out' 'Clock in'   # film each tap, in order
tools/verify/verify.sh at 32e078f4 film '#/today'              # same, on a copy of an older commit (port 18282)
```

Use it when a report is about how the screen moves, not what it ends up showing: "the page redraws", "it flashes",
"the whole page refreshes on clock out", "slow loading" on a page whose data is cached. A screenshot of the end state
and network timings both look fine for these bugs. The refresh-redraw bug (Oct 2026) took days because nobody filmed it.
`tools/verify/film.mjs` signs in on a 375px phone screen, clocks in (`VERIFY_CLOCKIN=0` skips), opens the route, reloads once so the
service worker and saved screen are warm, slows the CPU 4x, then records every frame Chromium paints (CDP screencast) for 2.5s
after each reload or tap. Chromium sends a frame only when the screen changes, so few frames means a still screen.
A frame is BLANK when one colour covers 99.5% of it or more (an empty page measures 100%, Today with content about 47%).
Each frame is printed with its ms after the reload or tap and its one-colour share; frames before 0 ms are the old screen and never count.
It saves `<VERIFY_OUT>/<time>-film-<route>/<run>/NN_<ms>.png` and `strip-<run>.png` (frames side by side, blank ones outlined red),
and exits 1 when any reload or tap showed a blank frame. Open the strip: a "Loading…" frame or a jump is not blank but is still a finding.

`at <commit>` checks whether a symptom is older than you think before you bisect: it makes a worktree of the commit, copies these
tools into it, runs `up`, the command, and `down`, then removes the worktree. Commits before Oct 21 (no `tools/verify`) did not start.

Feature map: `features/README.md` lists what to drive per page and what proves it.

## Evidence

Before and after, for any change people can see (`tools/push.sh` refuses a visible web change without it):

```bash
git commit ...                                         # the after shot must show HEAD
tools/verify/verify.sh up
tools/verify/verify.sh before-after '#/today' '#/finance'   # each route the change shows
```

It shoots each route on the merge-base with `origin/master` (a copy on port 18282, via `at`) and on HEAD, into
`/tmp/verify-evidence/before-after/<head>/`, and writes the `stamp` that `tools/quality/shot-gate.sh` checks. Look at
both before you push. Nothing visible changed (a `.svelte` file whose markup did not move)? `SHOT_SKIP='why' tools/push.sh`.

Screenshots go to `/tmp/verify-evidence/<UTC time>-<name>-{desktop,phone}.png` (override `VERIFY_OUT`). `down` never deletes them.
A proof is: the route you opened, the interaction (if any), the resulting state on screen, and the side effect when there is one
(query the scratch DB: `PGPASSWORD=... psql -h localhost -p 55432 -U company_user -d verify_18181`).
Open the PNGs and look; "exit 0" alone is not a proof. Put the paths and the command output in your report.
Use the real UI path, not test-only endpoints. The scratch DB has no real data, so seeding rows with SQL to reach a state is fine; say that you did.

## Cleanup

```bash
tools/verify/verify.sh down      # kills only the process group `up` started, drops verify_<port>, removes the run dir, keeps evidence
```

Run it after every failed attempt too. `tools/verify/verify.sh all '#/today' '#/audit'` does up, doctor, a shot per route and down (down runs even when a step fails).
Never `pkill java` or `pkill gradle`: the dev server and other agents' runs look the same.
Gradle's own daemon may stay alive after `down`; that is shared and harmless.

## Known limits

- Proven against `#/today` and `#/audit` only; the other pages in the map are described but not yet driven.
- The dev Postgres is shared: `up` creates and `down` drops only `verify_<port>`.
- Film runs in headless Chromium, not Brave or Safari on a phone; a clean film is evidence, the phone is the proof.
- Offline mode (service worker, outbox) is not covered by this harness.
