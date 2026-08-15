# VPS Migration Runbook — wayfinder chain to Oracle Cloud free tier

**The operational tool is `scripts/vps-migration-wizard.sh` (v2, session 65)** — it implements everything below as walkable stages, with modes: `full` (wayfinder chain + Coolify deploy) or `app-only` (just the app), and `tailnet` or `public` SSH. Run it instead of hand-executing this runbook. This file is the overview.

Move the unattended wayfinder chain (tmux daemon + opencode sessions) from the local Arch box to an Oracle Cloud **Always-Free Ampere A1** instance (2 OCPU / 12 GB — the current free-tier cap, lowered from 4 OCPU / 24 GB). **One daemon at a time; switch at a session boundary. Never run both.**

## What the chain depends on

| Dependency | Purpose | VPS action |
|---|---|---|
| `opencode2` CLI (`@opencode-ai/cli@0.0.0-next-17444`) | daemon's `api` calls + spawned sessions | install via npm (pinned) |
| opencode service (`opencode2 serve --service`) | daemon's local API endpoint (`$OC_BIN api …`) | start once after install |
| `~/.config/opencode/` | skills (wayfinder/implement/…), caveman plugin, `service.json` password, cli.json | copy whole dir |
| `~/.local/share/opencode/auth.json` | provider API keys (`opencode-go` = the chain's `deepseek-v4-flash`) | copy (126 B) — do NOT copy the 2.1 GB `opencode.db` |
| `gh` CLI + auth | issue tracker: `gh issue create/view/close/edit`, `gh api …/sub_issues` | install + `gh auth login` (token needs `repo` scope) |
| JDK 21 + Gradle 8.14.3 wrapper | build/test gates | `openjdk-21-jdk`; wrapper downloads Gradle |
| Postgres 18 (docker compose) | pre-commit gate + backend tests | `docker compose -f docker/docker-compose.yml up -d` |
| `.env` (repo root) | DB creds, JWT secret, test-user creds | copy real values from local box |
| `.wayfinder-loop.env` (gitignored) | ntfy topic + `WAYFINDER_MODEL` | copy |
| `bash scripts/setup-hooks.sh` | `.githooks` + ktlint CLI | run once after clone |
| k6 (optional) | pre-push load-test baseline | skip OK (pre-push warns + skips); or install aarch64 binary |
| git + GitHub push auth | sessions commit; you push | `gh auth git-credential` (from `gh auth login`) |

All components have aarch64 builds (JDK 21, Postgres 18 image, Gradle, ktlint jar). 2 OCPU / 12 GB is sufficient — the local box runs the same gates.

## Phase 0 — pre-flight (this machine)

1. Note the current session number and branch state:
   ```bash
   git log --oneline -1 && git status --short && gh issue list --state open
   ```
2. The branch `ralph/company-app-full-build` is ~280 commits ahead of origin — it must be pushed before the VPS can see it. **Push at the boundary (Phase 3), after session N's handoff lands** — that one push carries the handoff too. (Pushing now is safe if you want it out of the way: sessions commit locally, they don't push. The wizard's Phase 3 pushes kill-first-then-push, so the boundary push carries everything.)

## Phase 1 — Oracle console (human steps)

1. Sign in to Oracle Cloud → **Create a VM instance** (Compute → Instances → Create instance).
2. Image: **Ubuntu 24.04** (aarch64). Shape: **VM.Standard.A1.Flex** — set **2 OCPUs / 12 GB RAM** (the free-tier cap).
   - Capacity: A1 often reports *Out of capacity* in busy regions/ADs. Retry in your home region, or a different availability domain.
3. Boot volume: **47 GB** or larger (free tier includes 200 GB total block storage; the boot volume counts against it).
4. Add your **SSH public key** (`~/.ssh/id_ed25519.pub` — if you don't have one: `ssh-keygen -t ed25519`). The wizard generates it if missing.
5. **Assign a RESERVED public IPv4** if offered (keeps the nip.io domain stable). No extra ingress needed — only SSH (22) at first; the wizard later tightens the security list (tailnet mode removes 22; public mode replaces it with 51920 + 80/443).
6. Note the instance's public IP. `ssh ubuntu@<ip>` to verify.

## Phase 1b — Tailscale (the wizard does this)

The wizard installs Tailscale on the VPS and joins your tailnet (auth key from the Tailscale admin console). **All later admin access rides the tailnet** — in `tailnet` mode SSH is tailnet-only (no public SSH port; the strongest option — recommended); in `public` mode sshd also listens on 51920 for org admins, with fail2ban + key-only auth. The wizard also: **disables the node's key expiry** (180-day default = a tailnet lockout), and installs the **Oracle idle-reclaim heartbeat** (Oracle stops Always-Free instances idle 7 days; Coolify's memory footprint already covers the memory leg).

## Phase 2 — VPS bootstrap (one-time)

Run as root/ubuntu on the VPS.

```bash
# 1. Base packages
sudo apt update && sudo apt install -y openjdk-21-jdk git tmux curl unzip npm

# 2. Docker + compose plugin (for Postgres 18)
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu          # re-login for the group to take effect
sudo systemctl enable --now docker

# 3. GitHub CLI — sessions use `gh` heavily (issue tracker + sub-issues)
#    (type -t source install: https://github.com/cli/cli/blob/trunk/docs/install_linux.md)
sudo apt install -y gh
gh auth login          # token: classic PAT with `repo` scope (covers issues + git-credential)

# 4. opencode2 — same build the local box runs
sudo npm install -g @opencode-ai/cli@0.0.0-next-17444
opencode2 --version    # expect v0.0.0-next-17444

# 5. Migrate opencode config + provider keys (FROM the local box, e.g. rsync over SSH)
#    scp -r jayson@<local>:~/.config/opencode ~/.config/
#    scp    jayson@<local>:~/.local/share/opencode/auth.json ~/.local/share/opencode/
#    # do NOT copy ~/.local/share/opencode/opencode.db (2.1 GB session state — VPS starts fresh)

# 6. Start the opencode service (the daemon's `api` endpoint)
opencode2 serve --service          # daemonizes; check: opencode2 api get /api/model

# 7. Repo
git clone https://github.com/jsongalvez/company_app.git
cd company_app
git checkout ralph/company-app-full-build
git pull                            # at boundary time, picks up the handoff push

# 8. Secrets: copy the real files from the local box
#    scp jayson@<local>:~/.../company-app/.env .env
#    scp jayson@<local>:~/.../company-app/.wayfinder-loop.env .wayfinder-loop.env

# 9. Postgres 18
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps   # wait for healthy

# 10. Hooks + ktlint
bash scripts/setup-hooks.sh

# 11. Warm the gate once (downloads Gradle + deps; ~10-15 min first run)
./gradlew :backend:detekt :backend:ktlintCheck :backend:test
./gradlew :composeApp:compileKotlinDesktop
```

Verify: `git push --dry-run` shows only the branch; `gh issue list` lists #89/#110/#139.

## Phase 3 — the session-boundary switch (CRITICAL — never two daemons)

**The wizard's order differs from the runbook's original: kill FIRST, then push** (the push then carries anything a session committed up to the boundary; the handoff is re-derived after the kill and verified by sha256 on the VPS before the daemon starts).

1. **Wait for session N to complete** — `docs/agents/wayfinder-N-handoff.md` appears and is committed. The next session number is the one that handoff names.
2. On **this machine**: stop the local daemon.
   ```bash
   tmux kill-session -t wayfinder-loop
   ```
   Confirm: `tmux ls` shows no `wayfinder-loop`; `ps aux | grep wayfinder-loop.sh` is empty. (The wizard confirm-gates this and aborts if declined.)
3. On **this machine**: push the branch (carries the handoff):
   ```bash
   git push origin ralph/company-app-full-build        # pre-push gate ~3 min
   ```
4. On the **VPS**: pull, then bootstrap the daemon on the handoff.
   ```bash
   cd company_app && git pull
   tmux new -s wayfinder-loop
   ./scripts/wayfinder-loop.sh --bootstrap wayfinder-<N>-handoff.md
   ```
   Detach (`Ctrl-B D`). Watch the first spawn:
   ```bash
   tail -f .wayfinder-loop.log
   # expect: "spawned ses_... reading wayfinder-<N>-handoff.md"
   ```
5. Confirm the ntfy topic fires (`wayfinder session started`) and the new session starts working the map.

## Phase 4 — post-migration checks

- `gh issue view 89` — map untouched by the move; the new session claims its ticket on GitHub as usual.
- The VPS's pre-commit gate runs on every session commit (Postgres + backend tests) — watch the first one succeed end-to-end.
- Optional: install `k6` (aarch64 binary from GitHub releases) so pre-push load-test baselines run on the VPS too.

## Secrets checklist (what leaves this box)

- `.env` (DB creds, `JWT_SECRET`, `AUTH_DUMMY_PASSWORD`, k6 `TEST_USERNAME`/`TEST_PASSWORD`)
- `.wayfinder-loop.env` (`WAYFINDER_NTFY_TOPIC`, `WAYFINDER_MODEL`) — keep the rotated topic secret
- `~/.config/opencode/` incl. `service.json` (service password) + caveman plugin
- `~/.local/share/opencode/auth.json` (provider keys)
- `gh` token (`repo` scope) on the VPS

## Rollback

If the VPS setup fails mid-way: restart the local daemon from its state file — it is safe because it just resumes supervision of the session it already owns.

```bash
tmux new -s wayfinder-loop
./scripts/wayfinder-loop.sh        # resumes from .wayfinder-loop.state
```

The VPS daemon must NOT be started until the local one is dead (Phase 3 step 2 — the wizard kills first, then pushes in step 3). The branch is the shared artifact — whichever box hosts the daemon, commits flow to the same branch and GitHub issues.
