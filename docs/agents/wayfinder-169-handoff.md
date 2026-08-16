# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 66

## What this is

USER-PRIORITY follow-up to the #168 handoff: the wizard's config questions were going to leave future-them lost ("me in a couple months would be pretty lost"). This session made the **config UX self-explanatory** on `scripts/vps-migration-wizard.sh`: Enter-accepted defaults, one-line hints per field, a have-at-hand banner, a config summary with a review pause. The review loop then forced the rest of the hardening: validation gates on every free-typed value, remote-shell quoting, loud-stop aborts on every retry path. No map ticket was claimed; frontier stays EMPTY. Deliverable: 16 wizard commits `bfe43c5..ded5a07` + AGENTS.md pointer commit `b0ad92d`, all committed locally, NOT pushed (the switch push carries them).

**What changed in the wizard (v3):**

- **Config section** — every ask takes Enter for a default: MODE=`full`, SSH_MODE=`tailnet`, TS_HOSTNAME=`company-app-vps`, VPS_USER=`ubuntu`, REPO_URL derived (`git remote get-url origin`), DEPLOY_BRANCH derived (`git branch --show-current`), APP_DOMAIN blank→nip.io. One dim hint per ask (what/where/why). Re-runs: `[Enter keeps current: <value>]` (saved beats the passed default; `ask`'s optional 3rd arg is a **documented library extension** — see user decision 1). Intro banner + Configuration summary printed AFTER `banner` with a review pause (two prior fixes: the summary was wiped first by banner's `_clear`, then by stage 1's — the pause is what makes it readable; Ctrl-C there aborts with config-only state).
- **Validation gates (abort-before-write, all of them)** — MODE/SSH_MODE enums; charsets with first-char rules: TS_HOSTNAME `^[A-Za-z0-9][A-Za-z0-9-]*$` (no `_` — tailscale sanitizes it into a name mismatch), VPS_USER `^[A-Za-z][A-Za-z0-9_.-]*$`, DEPLOY_BRANCH charset + `git check-ref-format` (rejects `//`, `..`, leading dot, trailing `/` or `.`), REPO_URL charset + scheme `^[A-Za-z][A-Za-z0-9+.-]*://` OR `git@`, APP_DOMAIN blank-or `^[A-Za-z0-9.-]+$`, VPS_IP per-octet IPv4 + private/loopback/link-local rejection (127.*, 0.0.0.0, 10.*, 192.168.*, 169.254.*, 172.16-31.* — the `127.0.0.1` paste would otherwise configure the LOCAL box if the local user is `ubuntu`), TS_AUTHKEY charset at both ask sites (it lands in single quotes on the remote shell).
- **Remote-shell quoting** — every config-value interpolation into `vps`/`ssh` strings single-quoted: `git clone '$REPO_URL'`, `git switch '$DEPLOY_BRANCH'`, `git switch -C '$DEPLOY_BRANCH' origin/'$DEPLOY_BRANCH'`, `--hostname '$TS_HOSTNAME'` (both join sites). `grep -qiF -- "$TS_HOSTNAME"` at the tailnet skip (case-insensitive — cloud-init lowercases the OS hostname; a capitalized TS_HOSTNAME previously meant a fresh-key rejoin loop on every re-run).
- **Loud-stop aborts on every retry path** — the public-IP ssh retry, the tailnet-IP retry, and the tailscale-up retry all end in `abort` (were silent `set -e` deaths); the tailnet-IP is gated to a valid IPv4 before `write_env` (garbage/empty `tailscale ip -4` output can't poison the env); `curl … && echo || true` in the deploy verify.
- **Helpers** (below the marker): `IPV4_RE` (one regex, both IP gates), `latest_handoff` (sets global HANDOFF; NEVER wrap in `$()` — see the new register class), `check_authkey`.

**User decisions locked this session:** (1) **The ask() 3-arg extension stays.** The user checked the /wizard skill's source (aihero.dev + local SKILL.md: "never hand-edit the library" is addressed to the *agent*); decided to keep the extension anyway — `template.sh` stays pristine, only this committed wizard carries it, and regeneration of this file is unlikely. Header note (lines ~8-10) documents it as a fold-back candidate; the "wizard template" fog line is where it graduates. (2) This session runs the full wayfinder treatment: phased review loop, handoff doc, commit-local-not-pushed. (3) AGENTS.md gains a pointer to `/writing-for-agents` for agent-facing markdown edits.

## Session outcome

- **17 commits** (`bfe43c5` → `ded5a07` wizard, `b0ad92d` AGENTS.md). Pre-commit gate green every commit.
- `bash -n` clean. shellcheck still not installed on this box (standing follow-up).
- Config UX smoke-tested in isolation: first-run defaults, typed overrides, re-run saved-wins, blank-abort, charset rejections, octet-range IPv4, authkey formats, `latest_handoff` empty/dir/file shapes — all verified empirically.
- **`.wayfinder-vps.env` note**: if the user's earlier partial run saved config values, re-runs keep them — the new gates validate them, and a stale value aborts with an actionable message (type over it; a saved bad APP_DOMAIN needs its line deleted, the hint says so).

## Migration run log (Aug 15-16, session 66 continuation — **COMPLETE Aug 16, daemon live on the VPS**)

**Final leg (switch completed):** Coolify GitHub App (`jsongalvez-company-app` — webhook disabled, manual deploys only, minimal perms: Contents/Metadata/Email read-only; "Only on this account"); app resource on `ralph/company-app-full-build` (Dockerfile, `/backend/Dockerfile`, port 3023); **reserved public IP swap** — `VPS_IP` is now `140.245.56.102` (ephemeral → reserved; nip.io = `api.140.245.56.102.nip.io`); fresh `JWT_SECRET`/`AUTH_DUMMY_PASSWORD` in the Coolify env only (64 chars each — `openssl rand -hex 32`); TEST_* kept out of the deploy. Proxy quirks hit live: the auto-generated sslip.io domain must be replaced in the Domains tab (the "unsaved changes" form), and the container's network alias was deployment-suffixed while Caddy dialed the bare UUID (fixed by redeploy). **Exec-bit bug found repo-wide**: ALL `scripts/*.sh` + `.githooks/*` were 100644 in the index (Windows mount hid it) — `wayfinder-loop.sh` died instantly on the VPS (the daemon's tmux session vanished silently). Fixed in-repo (`744a0ea`, pushed): `git update-index --chmod=+x` on all shell scripts + hooks; `core.hooksPath=.githooks` confirmed on the VPS. Daemon started via the wizard's stage 35, `spawned ses_` in the log, **ntfy fired**.

The wizard is mid-run (~stage 20/36). Everything below was hit live; each item is either fixed, worked around, or a follow-up.

**Oracle console (create-instance) quirks:**
- A1 capacity is a lottery in ap-singapore-2. Console auto-click scripts fight the "Too many requests for the user" throttle; manual retries every few minutes (or the OCI CLI with backoff) are the honest path. A 2 VCN service limit exists — failed instance launches leave orphaned VCNs; **reuse an existing VCN/subnet instead of creating** (also unlocks the public-IPv4 radio, which stays dead while the subnet doesn't exist yet — subnets are created at launch).
- Instance NAME must equal TS_HOSTNAME — the OS hostname derives from it (a mismatch = tailnet rejoin loop on re-runs).
- Chosen: IMDSv2 auth header ON, all Oracle Cloud Agent plugins off, restore-lifecycle-state ON, shielded OFF (optional), custom boot volume 50 GB, in-transit encryption on (default), Oracle-managed keys, public subnet, reserved public IP attachable post-launch (mint under IP management → Reserved public IPs). Cost: $0/mo (2 OCPU/12 GB inside the 4/24 free cap; 50 GB inside 200 GB).

**VPS_USER bug (fixed):** the pre-hardening partial run had saved `VPS_USER=jayson` (the old prompt had no hint); the wizard ssh'd as jayson → `Permission denied (publickey)` at the verify stage and every stage after. The VM only ever has the `ubuntu` user. Fix: type `ubuntu` over the kept value (or edit `.wayfinder-vps.env`).

**Private-repo clone (fixed, wizard gap):** `gh auth login --with-token` authenticates gh but does NOT wire git's credential helper → `git clone https://…` fails with "could not read Username". Fix: `gh auth setup-git` on the VPS + `rm -rf ~/company_app` (the failed clone's leftovers). **The repo is PRIVATE** — the Coolify app stage's "Public Repository" will fail when reached; plan a Coolify GitHub App or a `https://<token>@github.com/…` URL.

**gradlew exec bit (fixed in-repo, commit `33d1962`):** the repo index tracked `gradlew` as 100644 — hidden locally by the Windows mount's `core.fileMode=false`, but VPS clones (ext4, real modes) got a non-executable wrapper → `./gradlew` → Permission denied on the gate. Fix: `git update-index --chmod=+x gradlew` + commit; VPS side `chmod +x`. Check the repo for other wrappers with the same hidden-mode issue.

**Timezone-dependent tests (worked around; REAL TEST BUG — follow-up):** the suite silently requires the JVM default TZ to be Asia/Manila: `createBranchDayForToday` seeds with `LocalDate.now(BranchDayService.manilaZone)` (DatabaseTestHelper.kt:308) while assertions and clock-in seeding use JVM-default `LocalDate.now()` (RouteValidationTest.kt:898/:989, DatabaseTestHelper.kt:349, ReliefAccess/RemittanceLine). On a UTC box during 16:00-23:59 UTC, entire classes fail — RouteValidation with ComparisonFailure, ReliefAccess/RemittanceLine with a ForbiddenException cloud (clock-ins seeded for UTC-today aren't "active" on Manila-today). Fix on the VPS: `sudo timedatectl set-timezone Asia/Manila` + `sudo pkill -f GradleDaemon` (old daemons keep the old TZ). Proper fix for a future session: a TZ fixture in the test base or manilaZone-consistent seeds/asserts.

**Test-DB init quirk (worked around):** `company_app_test` is created ONLY by `docker/init-test-db.sql`, which runs at the Postgres container's first boot (init scripts don't re-run). `DROP DATABASE` (done during diagnosis) breaks every test with "database does not exist" (`ensureDatabase` connects, never creates). Recreate by hand: `CREATE DATABASE company_app_test` as `company_user`.

**Wizard improvements to fold in (follow-up batch — fog-line material):** (a) bootstrap stage: `timedatectl set-timezone Asia/Manila`; (b) gh-auth stage: `gh auth setup-git` after the token login; (c) Postgres stage: guard-create `company_app_test`; (d) test TZ fixture; (e) gate-launch: the wizard's relaunch is fine — the earlier confusion was a stale-log poll after a manual `rm`; nothing to change.

## Review outcome (phased loop, 12 passes + P5 + 2 loop-backs)

- **Pass 1**: 2 HARD (the library-edit "do not hand-edit" breach — **reclassified SOFT by user decision** after checking the skill's source; unquoted config values at remote-shell sites → quoting + charset gates) + SOFTs (hints, banner overclaim, summary secrets line — fixed).
- **Pass 2**: 3 HARD — leading-dash bypasses, all empirically confirmed: DEPLOY_BRANCH `-d` → `git switch --detach` → VPS daemon on detached HEAD → session commits discarded; VPS_USER `-x` → ssh getopt breakage; REPO_URL `-x://evil` scheme pass-through. Fixed with first-char gates. Also 1 ESCALATE (APP_DOMAIN/VPS_IP injection) — rejected as inert on the `$()` vector.
- **Pass 3**: 0 HARD; HANDOFF empty-vs-regex ordering fixed (the regex had made the accurate "no handoff found" abort dead).
- **Pass 4 (exit attempt)**: 1 HARD — the config summary was wiped by `banner`'s `_clear` before it was readable. Fixed (summary after banner).
- **Pass 5**: 0 HARD — but the summary was then wiped by stage 1's clear; review pause added.
- **Pass 6**: 0 HARD — pause wording overclaim fixed ("redo the config" → "re-asks with saved values").
- **Pass 8 (exit attempt)**: 0 HARD; the honest catch of the session — my pass-2 ESCALATE rejection was only HALF right: `$()` in a variable value is never re-parsed (true), but an embedded `"` DOES break out of surrounding double quotes → local injection. APP_DOMAIN + VPS_IP got charsets; `git check-ref-format` added; two dead curl/ssh recoveries revived.
- **Pass 9**: 2 HARD — TS_AUTHKEY remote quote-breakout (last unvalidated pasted value in a remote quote context; the class I'd just closed elsewhere); sibling retry silent-death (`|| TS_IP=""` fixed the first derivation but not the retry inside the brace group).
- **Pass 10-12**: 0 HARD each; fixed: case-insensitive hostname verify, public-IP gate (loopback/private), abort-idiom at two push sites, "(first run)" qualifier, TS_IP garbage gate, known_hosts hint.
- **P5 (architecture-depth + hygiene)**: 3 ARCH in-ticket (IPV4_RE single-source; `latest_handoff` helper — the two-adapter rule fired on the derive shape; `check_authkey`; dead empty-aborts deleted) + 1 graduate: **ask()-3rd-arg fold-back into the wizard template** (absorbs into the standing "wizard template" fog line; the helpers ride it too).
- **P5 loop-back 1**: 1 HARD — **NEW LESSON-CLASS, added to the register** (`docs/agents/code-review-loop.md`): **loud-stop swallow** — `abort()` inside `$( )` captures the message into the substitution, `exit` kills only the subshell, the failing assignment trips `set -e` → silent death. My own P5 batch introduced it; empirically confirmed. Fix: helpers set globals, call sites plain statements.
- **P5 loop-back 2**: 1 HARD — `ls -t` recursed into a handoff-named DIRECTORY, shadowing the empty check with the wrong message; `ls -td` + the `-f` check fix it. Final pass: **0 HARD — EXIT**.
- **Accepted SOFTs (dispositioned, re-rated by ≥2 lenses each — ride list exceeds the ≤3 cap; each is a deliberate cost, most are template-graduate material)**: (a) scp-style `user@host:path` URLs rejected — scheme/git@ forms cover this repo's world, message names them; (b) saved-value display may echo a token-bearing origin — local-only, non-secret; (c) Ctrl-D accepts defaults — intended (Enter-equivalent); (d) banner+review double pause — the review pause is load-bearing for summary readability; (e) private-IP gate blind spots (CGNAT/multicast/224+/TEST-NET) — the ssh-verify stage is the real gate, self-healing; (f) "(first run)" banner on stale-env re-runs — recovery paths self-announce; (g) unreadable `docs/agents/` misdiagnosed as "no handoff found" — 1-sighting, non-load-bearing.
- **ESCALATE rejected with proof**: APP_DOMAIN/VPS_IP injection via `$()` — variable expansion is never re-parsed (the `"` vector was real and fixed; the `$()` vector was not).

## Patterns + learnings

- **The wizard's own UX review is a microcosm of the whole loop discipline.** Every pass found a new way the config UX could lie to its human: the summary that printed-and-vanished twice, the pause that promised a redo it didn't deliver, the "invalid IPv4" gate that passed 999.999.999.999, the case-sensitive hostname check that silently burned an auth key per re-run. The loops kept firing because each pass re-derived the FLOW from the final file instead of trusting the previous pass — the exit-pass catch of the `"`-breakout is the payoff.
- **Pasting is the attack surface.** Everything the human pastes (oracle IP, tailscale key, PAT) rides inside quote contexts — local AND remote. The discipline that ended this session's injection classes: every ask/ask_secret value gets a charset gate + single-quoting at every interpolation site + `grep -qF`/`-i` where values meet patterns. TS_AUTHKEY's gate came only when a lens re-rated the "key format constrains it" acceptance — the class was closed elsewhere by then, so leaving it open was the inconsistency.
- **The two-adapter rule works on bash.** `IPV4_RE` and `latest_handoff` were both flagged independently by the P5 pair; the divergence was already visible (the octet regex had two spellings).
- **Abort-in-subshell is a class, not a one-off** (register: loud-stop swallow). Any future `$(helper_that_aborts)` is silent death — the register row carries the fix shape.
- Prior-session patterns unchanged: claim-first N/A (no ticket), facts-via-code, one-deliverable-per-session, commit local not pushed, gate green before commit.

## Current frontier (verified live post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Nothing on the map changed this session (no ticket claimed, no fog graduated). The map's #89 Notes/runbook will be superseded once the migration runs.

## Recommended next pick

**UNCHANGED — USER PRIORITY: RUN the wizard — `bash scripts/vps-migration-wizard.sh` (repo root on this box).** The session's hardening makes the run safer, not longer: config is now Enter-through (defaults + hints + a review pause before anything happens), every pasted value is validated with a clear abort, every retry path fails loud instead of silent, and `latest_handoff` cannot mis-derive. Plan ~2.5 h. Stage order matters as before: the switch (stage 30-31) is the only irreversible-feeling moment and self-checks (handoff sha256 parity, rollback command printed). After the run: the wayfinder chain runs on the VPS; next map session works from there (same branch, same issues — the branch is the shared artifact).

Standing fog lines (unchanged from #168; the migration must land first): the P5 ARCH graduates (mirror-only KeepLastByKey split; handler state-less launch; update-one map-if sub-shape; stale-response guards; unifier-family-naming-coherence), **wizard-template helpers (absorbs THIS session's graduates: ask() 3rd-arg default + the saved-value display, `IPV4_RE`, `latest_handoff`, `check_authkey`, `_wait_until`, guarded-ssh/vps-retry idiom, `ensure_pushed`, `ask_env` — the fold-back instruction in the wizard's header note is the pointer)**, DayStatus shared/domain move, #92 Q4/Q5 403-refresh machinery, #158 ARCH pair + #160 graduates (ilike unifier; screen-scoped-VM-seam), request-flow grant/deny UI gap, k6 for branch-scoped writes (needs #106 seeding), then #110 / root-AGENTS.md pre-push-iOS line / #139 / #106.

## How to drive the next session (wayfinder "Work through the map")

1. If the migration HASN'T run: nothing is takeable — the wizard is the assignment; grill the user to run it or re-scope.
2. If the migration HAS run (you are ON the VPS): load the map (https://github.com/jsongalvez/company_app/issues/89), wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), decision-loop for HITL. Frontier empty → fog-graduation: grill the user to graduate one standing fog line (the handler state-less launch is the AFK-able pick; the mirror-only KeepLastByKey split rides the naming-coherence decision), or take an AFK build. Any new ticket: child of #89 (POST `/issues/89/sub_issues`), claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **check the review window for runner daemon commits and re-scope**; grep-verify batch fixes; read fog-line counts as claims to verify in code. The register now includes the **loud-stop swallow** class — check any `$(...)` wrapping a helper that can abort.
4. Post the answer as a resolution comment, close, append to #89's Decisions-so-far + rewrite the frontier paragraph.
5. One-ticket-per-session. End with `docs/agents/wayfinder-<N>-handoff.md` in this format. When editing agent-facing markdown (this file, AGENTS.md, docs/agents/), load `/writing-for-agents` first (new AGENTS.md pointer, commit `b0ad92d`).

## Map state at session-end

Unchanged — no tickets touched, no fog moved. The migration itself is the standing next move, tracked in this handoff + the runbook + the wizard, not on the map.

## Constitution update (this session)

**New register class: loud-stop swallow** (see the review outcome; row appended to `docs/agents/code-review-loop.md` — origin wayfinder-169 P5 loop-back, first occurrence). No other register candidates. The reclassified library-edit finding (human/agent scope of the /wizard skill's "never hand-edit") is a process clarification, recorded in this handoff's decisions, not a fired class.

## Critical follow-ups (human)

- **The loop-infra zombie-detector fix STILL needs a real exercise** (from #167/#168): a deliberate 10-min-sleep sub-agent probe during a review phase.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **shellcheck** still not installed — `shellcheck -S warning scripts/vps-migration-wizard.sh` before or during the wizard run (first clean pass is a no-op; the wizard got `bash -n` only).
- **Optional runbook amendment** (post-migration): Phase 1 is now tailscale-first, Phase 2 gains the Coolify + hardening stages the wizard implements; the runbook and wizard should agree after the run.
- **The wizard now defaults REPO_URL/DEPLOY_BRANCH from this checkout** — if the wizard is ever re-run for the future app owner's repo, the derivation follows their checkout; nothing to change.
- **Migration-run follow-ups** (from the run log above): the wizard's bootstrap should pin `Asia/Manila`, the gh-auth stage needs `gh auth setup-git`, the Postgres stage should guard-create `company_app_test`, the Coolify stage should skip when the API already answers (it re-runs the installer every re-run — harmless upgrade dance, ~1 min), the app-resource stage should detect private repos and point at the GitHub App path, and the test suite needs a TZ fixture (RouteValidation/ReliefAccess/RemittanceLine currently depend on the machine TZ). The Coolify GitHub App (webhook off) is the private-repo mechanism; manual deploys only.
- **Check the repo for other Windows-mount-hidden exec bits** (gradlew + ALL scripts + hooks were 100644 in the index — fixed `744a0ea`; any future file added from the Windows mount should get `git update-index --chmod=+x` if it needs exec).

## Suggested skills for next session

- **`/wizard`** — NOT for authoring anymore; re-read only if a re-run fails.
- **`/wayfinder`** — "Work through the map" once the chain is on the VPS (frontier empty → fog-graduation or AFK build).
- **`/writing-for-agents`** — any agent-facing markdown edit (AGENTS.md now points at it).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build ticket; register watch-lenses: **truth-class**, **keyed-mirror ordering**, **loud-stop swallow** (NEW — `$(...)` around anything that can abort).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies to all human-facing questions).
