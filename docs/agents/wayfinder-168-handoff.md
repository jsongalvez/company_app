# Handoff — Wayfinder Map #89 (Frontend Rebuild), Session 65

## What this is

Session 65 was the USER-PRIORITY pick from the #167 handoff: **author the VPS-migration wizard**. Scope grew this session on explicit user direction — Tailscale, hardening, and the **Coolify deploy** (docs/architecture.md §5) were folded in: *"we're essentially prioritizing the cloud step of the development."* No map ticket was claimed; the frontier stays EMPTY. The deliverable: `scripts/vps-migration-wizard.sh` (committed `bd78b09`), runnable now.

**What the wizard does (33 stages, ~145 min, run on THIS Arch box — the repo on the external mount):**

- Phase 0 — pre-flight: branch state + open issues; optional early push of `ralph/company-app-full-build` (~276 ahead).
- Phase 1 — SSH key (none existed on this box — wizard generates `~/.ssh/id_ed25519`), Oracle console VM create (Ubuntu 24.04 aarch64, A1.Flex 2 OCPU/12GB, boot ≥47GB, **reserved public IP**), public-IP ssh verify, capture `VPS_IP`.
- Phase 2 — Tailscale: authkey (ask_secret, consumed once) → install + join tailnet on the VPS → `TS_IP` derived; **all later stages ride the tailnet** (ssh/scp helpers).
- Phase 3 — bootstrap over tailnet: base packages, Docker, `gh auth login --with-token` (PAT ask_secret, never written to disk), opencode2 pinned, opencode config + auth.json scp (**NOT** opencode.db), `opencode2 serve --service`, repo clone (graceful if the branch isn't on origin yet — the switch push lands it), `.env` + `.wayfinder-loop.env` scp, Postgres 18 compose up, setup-hooks, gate warm (nohup + poll).
- Phase 4 — harden (user chose **tailnet-only SSH** over arch.md's public 51920+fail2ban): ufw (default deny; allow in on tailscale0; 80/443 public; 41641/udp), sshd `PermitRootLogin no` + `PasswordAuthentication no`, unattended-upgrades, Oracle security list edited to drop 22/0.0.0.0/0 (2nd firewall layer).
- Phase 5 — Coolify: install, dashboard first run (admin email/password captured to `.wayfinder-vps.env`), **backend Dockerfile** (authored + committed this session — see below), Postgres resource (env values read live from local `.env` for copy-paste), app resource (Public Repository — no GitHub App needed, repo is public; branch `ralph/company-app-full-build`; Dockerfile build pack; env block printed from local `.env` with `DB_HOST=postgres` + `APP_HOST=0.0.0.0`; domain `api.<VPS_IP>.nip.io` + Let's Encrypt), `/health` HTTPS verification.
- Phase 6 — the switch (CRITICAL — never two daemons): boundary check (latest handoff detected from `docs/agents/`), push (carries the handoff), kill local daemon (confirm gate + rollback note), VPS daemon via `tmux new-session -d … --bootstrap <handoff>`, verify `spawned ses_` in `.wayfinder-loop.log` + ntfy confirm, post checks.

**User decisions locked this session (HITL gates):** (1) SSH via Tailscale only — simpler + stronger than arch.md §5's public non-standard port + fail2ban; (2) full Coolify install + deploy in the wizard; (3) **nip.io domain** — falsification ran: no-domain/HTTP **killed** (cleartext login creds on a public API — HARD), DuckDNS survives but adds account+token+updater with zero stability benefit once the Oracle IP is reserved; nip.io survives with zero moving parts; both are throwaway scaffolding, real domain (~$10/yr) later. nip.io dies → admin access unaffected (tailscale), cert keeps working till expiry, re-point in Coolify = 5 min. GitHub Pages was the user's question — **killed**: static-only, cannot host the Javalin API. (4) `gh auth login --with-token` over ssh (PAT) rather than interactive. (5) wizard committed to `scripts/` (user choice).

**Backend Dockerfile** (`backend/Dockerfile`, same commit): multi-stage — `eclipse-temurin:21-jdk` + Gradle wrapper `:backend:installDist` (verified locally, BUILD SUCCESSFUL) → `eclipse-temurin:21-jre`, `touch .env` (dotenv-kotlin throws on missing file; real values come from Coolify env vars which take precedence), `ENTRYPOINT ["bin/backend"]`. Runtime facts baked from `AppConfig.kt`: requires APP_PORT, DB_HOST, DB_PORT, POSTGRES_DB/USER/PASSWORD, JWT_SECRET/ISSUER/AUDIENCE, AUTH_DUMMY_PASSWORD; TEST_* optional (kept off in prod). Health check: `GET /health` (HealthRoutes.kt).

## Session outcome

- **Committed `bd78b09`** on `ralph/company-app-full-build` (3 files, +550): `scripts/vps-migration-wizard.sh`, `backend/Dockerfile`, `.gitignore` (+`.wayfinder-vps.env`). Pre-commit gate fully green (ktlint/detekt/tests/cleanliness/shared-compile/Postgres).
- `bash -n` clean; shellcheck not installed on this box (note in follow-ups). `chmod +x` set.
- Static trace done per wizard skill: every captured value (VPS_IP, TS_IP, GH_PAT, TS_AUTHKEY, COOLIFY_ADMIN_EMAIL/PASSWORD) lands where scoped (env file or consumed-once); every `set_secret`/`set_var` unused by design (no new GitHub secrets — Coolify pulls the PUBLIC repo; gh PAT consumed on the VPS only).
- `.wayfinder-vps.env` (gitignored) = wizard state; re-runs are idempotent (ask offers existing values; tailnet join + postgres-up skip when already done).

## Patterns + learnings

- **Falsification works on infra choices too.** The domain decision went through the same kill-attempts discipline as a code design: no-domain died on a HARD (cleartext creds on a public API — the app has login + JWT), DuckDNS survived but carried a moving part that a reserved IP makes pointless, nip.io survived with none. The verdict came out of the kill attempts, not taste. User accepted it and asked the sharp follow-up ("how do I access the VPS if nip dies?") — answer: admin path (tailscale) never depends on the app's public name; cert expiry is the only clock.
- **User's "too complicated" on the first 21-stage pass → group by phase, don't cut steps.** The re-pitch that landed grouped the runbook's micro-steps into phases (pre-flight/VM+access/bootstrap/harden/deploy/switch) and let the wizard skill's stage-per-step structure carry the detail. User then said "take as many stages as you need, I'll just follow the steps" — the complexity complaint was about the *pitch* (a wall of steps), not the *procedure*.
- **Ask the user early about infra they already own.** The tailscale reveal (and the Coolify recall) came from asking "how should SSH be exposed" and "include the Coolify deploy?" — both changed the design materially. The runbook predates both.
- **Wizard scoping must read the runtime code, not just the runbook.** AppConfig.kt's required env keys + dotenv's throw-on-missing-file drove the Dockerfile's `touch .env`; `installDist` was verified locally before committing the Dockerfile; `GET /health` became the deploy verification.
- Prior-session patterns unchanged: claim-first N/A (no ticket), facts-via-code, one-deliverable-per-session, commit local not pushed, gate green before commit.

## Current frontier (verified live post-session)

**Frontier EMPTY** — no open unblocked child of #89. Open standalone tickets unchanged: #139 (OpenAPI map), #110 (hardcoded-month test fix). Nothing on the map changed this session (no ticket claimed, no fog graduated) — but note the map's #89 Notes/runbook will be materially superseded by the migration once the wizard runs (the chain moves to the VPS; this box stops hosting it).

## Recommended next pick

**USER PRIORITY (explicit): RUN the wizard — `bash scripts/vps-migration-wizard.sh` (from the repo root on this box).** It is HITL by design — the human drives the Oracle console, Tailscale admin, and Coolify dashboard; the wizard captures and verifies. Plan ~2.5 h. Stage order matters: the switch (stage 30-31) is the only irreversible-feeling moment and it self-checks (boundary handoff committed + local daemon confirmed dead before the VPS daemon starts; rollback command printed). After the run: the wayfinder chain runs on the VPS; next map session works from there (same branch, same issues — the branch is the shared artifact).

Standing fog lines (for sessions AFTER the migration lands): the P5 ARCH graduates (mirror-only KeepLastByKey split — marker: third consumer or ReliefInvite touching `lastByKey` without the guard; handler state-less launch — kills the #163 snag; update-one map-if sub-shape; stale-response guards — DOCUMENTATION ONLY; unifier-family-naming-coherence — `ActionTracker.begin` vs `tryBegin`, rides the mirror-only split), DayStatus shared/domain move, #92 Q4/Q5 403-refresh machinery, #158 ARCH pair + #160 graduates (ilike unifier; screen-scoped-VM-seam), request-flow grant/deny UI gap, k6 for branch-scoped writes (needs #106 seeding), then #110 / root-AGENTS.md pre-push-iOS line / #139 / #106.

## How to drive the next session (wayfinder "Work through the map")

1. If the migration HASN'T run: nothing is takeable — the wizard is the assignment; grill the user to run it or re-scope.
2. If the migration HAS run (you are ON the VPS): load the map (https://github.com/jsongalvez/company_app/issues/89), wayfinder skill, tracker conventions (`docs/agents/issue-tracker.md` → "Wayfinding operations"), decision-loop for HITL. Frontier empty → fog-graduation: grill the user to graduate one standing fog line (the handler state-less launch is the AFK-able pick; the mirror-only KeepLastByKey split rides the naming-coherence decision), or take an AFK build. Any new ticket: child of #89 (POST `/issues/89/sub_issues`), claim before work.
3. Build per module AGENTS.md + the phased code-review loop (`docs/agents/code-review-loop.md`) with `git diff <last-pass-commit>` per pass; **check the review window for runner daemon commits and re-scope**; grep-verify batch fixes; read fog-line counts as claims to verify in code.
4. Post the answer as a resolution comment, close, append to #89's Decisions-so-far + rewrite the frontier paragraph.
5. One-ticket-per-session. End with `docs/agents/wayfinder-<N>-handoff.md` in this format.

## Map state at session-end

Unchanged — no tickets touched, no fog moved. The migration itself is the standing next move, tracked in this handoff + the runbook + the wizard, not on the map.

## Constitution update (this session)

No new register candidates. The falsification-on-infra-choices work is process, not code; the wizard's own ssh helpers (`vps`/`vpsscp`) are a documented script shape, not a fired class. The domain-verdict reasoning (nip.io over DuckDNS/no-domain) is recorded in this handoff's decisions — the standing decision-loop frame (dev-stage, falsify every claim, accepted costs named) applied.

## Critical follow-ups (human)

- **The loop-infra zombie-detector fix STILL needs a real exercise** (from #167 handoff): two sessions of 1-3 min phases under the 9-min stall threshold — the `active_children` reset branch never triggered. A deliberate 10-min-sleep sub-agent probe during a review phase is overdue.
- **ntfy topic rotation** from session 61 stands (`wf-0w9uaukg` in the gitignored `.wayfinder-loop.env`).
- **shellcheck** is not installed on this box — the wizard got `bash -n` only. Run `shellcheck -S warning scripts/vps-migration-wizard.sh` before or during the wizard run (first clean pass is a no-op).
- **Optional runbook amendment** (post-migration): the runbook's Phase 1 (public-IP ssh) is now the tailscale-first flow; Phase 2 gains the Coolify + hardening stages the wizard implements. Worth one docs commit after the run so the runbook and wizard agree.

## Suggested skills for next session

- **`/wizard`** — NOT for authoring anymore; the wizard exists. Re-read it only if a re-run fails.
- **`/wayfinder`** — "Work through the map" once the chain is on the VPS (frontier empty → fog-graduation or AFK build).
- **`/implement` + `docs/agents/code-review-loop.md`** — for any build ticket; register watch-lenses: **truth-class** (KDoc/comment claiming what code doesn't do) and **keyed-mirror ordering** (don't confuse with substitution-family stamp / ordering-family sentStamp).
- **`docs/agents/decision-loop.md`** — for any HITL design ticket (standing frame applies to all human-facing questions).
