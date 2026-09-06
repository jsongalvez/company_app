# VPS Migration Runbook — wayfinder chain to Oracle Cloud free tier

**The operational tool is `scripts/vps-migration-wizard.sh` (v2, session 65)** — it implements everything below as walkable stages, with modes: `full` (wayfinder chain + Coolify deploy) or `app-only` (just the app), and `tailnet` or `public` SSH. Run it instead of hand-executing this runbook. This file is the overview.

Move the unattended wayfinder chain (tmux daemon + opencode sessions) from the local Arch box to an Oracle Cloud **Always-Free Ampere A1** instance (2 OCPU / 12 GB — the current free-tier cap, lowered from 4 OCPU / 24 GB). **One daemon at a time; switch at a session boundary. Never run both.**

## What the chain depends on

| Dependency | Purpose | VPS action |
|---|---|---|
| `opencode2` CLI (`@opencode-ai/cli@0.0.0-next-17444`) | daemon's `api` calls + spawned sessions | install via npm (pinned) |
| opencode service (`opencode2 serve --service`) | daemon's local API endpoint (`$OC_BIN api …`) | start once after install |
| `~/.config/opencode/` | skills (wayfinder/implement/…), caveman plugin, `service.json` password, cli.json | copy whole dir |
| `~/.local/share/opencode/auth.json` | provider API keys (`opencode` / OpenCode Zen = the chain's `Ox Alpha Free`) | copy (126 B) — do NOT copy the 2.1 GB `opencode.db` |
| `gh` CLI + auth | issue tracker: `gh issue create/view/close/edit`, `gh api …/sub_issues` | install + `gh auth login` (token needs `repo` scope) |
| JDK 21 + Gradle 8.14.3 wrapper | build/test gates | `openjdk-21-jdk`; wrapper downloads Gradle |
| Android SDK (cmdline-tools, platform 36, build-tools 36.0.0) | Android compile + unit tests (targeted/CI validation) | `sdkmanager` (wizard stage); on aarch64 first add qemu-user-static binfmt + amd64 multiarch libs — AGP's aapt2 is x86_64-only |
| Postgres 18 (docker compose) | backend tests (run on demand) | `docker compose -f docker/docker-compose.yml up -d` |
| `.env` (repo root) | DB creds, JWT secret, test-user creds | copy real values from local box |
| `.wayfinder-loop.env` (gitignored) | ntfy topic + `WAYFINDER_MODEL` | copy |
| `bash scripts/setup-hooks.sh` | `.githooks` + ktlint CLI | run once after clone |
| k6 (optional) | manual load-test runs | skip OK; or install aarch64 binary |
| git + GitHub push auth | sessions commit to `master` and push immediately | `gh auth git-credential` (from `gh auth login`) |

All components have aarch64 builds (JDK 21, Postgres 18 image, Gradle, ktlint jar). The one x86_64-only toolchain piece is AGP's `aapt2` (no `linux-arm64` on the Maven repo) — handled by qemu-user-static binfmt + amd64 multiarch libs in the SDK step below. 2 OCPU / 12 GB is sufficient — the local box runs the same gates.

## Phase 0 — pre-flight (this machine)

1. Note the current session number and repo state:
   ```bash
   git log --oneline -1 && git status --short && git fetch origin && git status -sb && gh issue list --state open
   ```
2. Sessions integrate **directly to `master` and push immediately** — unpushed work should be rare. Push anything local now (`git push`); at a session boundary the worktree is clean by contract. The handoff packet is gitignored and travels by scp in Phase 3, never by push.

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

# 7. Repo (default branch = master; sessions commit to master and push immediately)
git clone https://github.com/jsongalvez/company_app.git
cd company_app

# 8. Secrets: copy the real files from the local box
#    scp jayson@<local>:~/.../company-app/.env .env
#    scp jayson@<local>:~/.../company-app/.wayfinder-loop.env .wayfinder-loop.env

# 9. Postgres 18
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps   # wait for healthy

# 10. Hooks + ktlint
bash scripts/setup-hooks.sh

# 11. Android SDK — targeted Android compile/test validation needs it.
#     On aarch64 (the A1) AGP's aapt2 is x86_64-only, so first make x86_64
#     binaries runnable: qemu-user-static binfmt + the amd64 glibc loader.
#     (AGP 8.12 publishes no linux-arm64 aapt2 — checked against the Maven repo.)
if [ "$(uname -m)" = aarch64 ]; then
  sudo apt-get install -y qemu-user-static binfmt-support
  # amd64 libs (loader at /lib64/ld-linux-x86-64.so.2) — the ports mirror has no
  # amd64 binaries, so add archive.ubuntu.com for the amd64 arch.
  sudo dpkg --add-architecture amd64
  sudo tee /etc/apt/sources.list.d/amd64.sources >/dev/null <<APTEOF
Types: deb
URIs: http://archive.ubuntu.com/ubuntu/
Suites: noble noble-updates noble-backports
Components: main universe restricted multiverse
Architectures: amd64
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
APTEOF
  sudo apt-get update && sudo apt-get install -y libc6:amd64 libstdc++6:amd64 zlib1g:amd64
fi
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp && curl -fSLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
rm -f /tmp/commandlinetools-linux-11076708_latest.zip
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
printf '\n# Android SDK (wizard)\nexport ANDROID_HOME="$HOME/android-sdk"\nexport ANDROID_SDK_ROOT="$ANDROID_HOME"\nexport PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"\n' >> ~/.bashrc
# sdk.dir in repo local.properties carries SDK discovery for the daemon's
# non-interactive shells (no .bashrc read); AGP also honors ANDROID_HOME.
echo "sdk.dir=$HOME/android-sdk" >> ~/company_app/local.properties

# 12. Warm the gate once (downloads Gradle + deps; ~10-15 min first run)
./gradlew :backend:detekt :backend:ktlintCheck :backend:test
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest
```

Verify: `git push --dry-run` reports master up to date; `gh issue list` shows the live tracker.

## Phase 3 — the session-boundary switch (CRITICAL — never two daemons)

**The wizard's order differs from the runbook's original: kill FIRST, then push** (the push then carries anything a session committed up to the boundary; the handoff packet is re-derived after the kill, scp'd to the VPS, and verified by sha256 before the daemon starts).

1. **Wait for session N to complete** — the handoff packet `.wayfinder/handoffs/wayfinder-N-handoff.md` appears (gitignored runtime state — it is never committed; map #329 #336). The next session number is the one that handoff names.
2. On **this machine**: stop the local daemon.
   ```bash
   tmux kill-session -t wayfinder-loop
   ```
   Confirm: `tmux ls` shows no `wayfinder-loop`; `ps aux | grep wayfinder-loop.sh` is empty. (The wizard confirm-gates this and aborts if declined.)
3. On **this machine**: push any residual local commits (direct-to-master sessions
   push immediately, so this is normally a no-op — belt and braces before the switch):
   ```bash
   git push origin master        # hooks are bookkeeping; push is network-only
   ```
4. On the **VPS**: pull, copy the packet by scp, then bootstrap the daemon on it.
   ```bash
   cd company_app && git pull
   ```
   From **this machine** (packet is gitignored — it does not ride the push):
   ```bash
   mkdir -p <vps>:company_app/.wayfinder/handoffs   # via ssh, or: ssh <vps> 'mkdir -p ~/company_app/.wayfinder/handoffs'
   scp .wayfinder/handoffs/wayfinder-<N>-handoff.md <vps>:~/company_app/.wayfinder/handoffs/
   ```
   Then on the **VPS**:
   ```bash
   tmux new -s wayfinder-loop
   ./tools/wayfinder/wayfinder-loop.sh --bootstrap wayfinder-<N>-handoff.md
   ```
   Detach (`Ctrl-B D`). Watch the first spawn:
   ```bash
   tail -f .wayfinder-loop.log
   # expect: "spawned ses_... reading wayfinder-<N>-handoff.md"
   ```
5. Confirm the ntfy topic fires (`wayfinder session started`) and the new session starts working the map.

## Phase 4 — post-migration checks

- `gh issue view 89` — map untouched by the move; the new session claims its ticket on GitHub as usual.
- The VPS's hooks are bookkeeping only (map #329) — validation is targeted/on-demand plus asynchronous CI.
- Optional: install `k6` (aarch64 binary from GitHub releases) for manual load-test runs.
- **CORS: the API ships with zero CORS config — correct for the native KMP clients (Android/desktop/iOS don't enforce CORS) and for same-origin web. The moment a browser client on ANOTHER origin appears (web dashboard, web build of the app), register Javalin's bundled `CorsPlugin` with that origin whitelisted — and note the auth is JWT-bearer, not cookie, so no credentials-mode restrictions apply.**

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
./tools/wayfinder/wayfinder-loop.sh        # resumes from .wayfinder-loop.state
```

The VPS daemon must NOT be started until the local one is dead (Phase 3 step 2 — the wizard kills first, then pushes in step 3). Master is the shared artifact — whichever box hosts the daemon, commits land on `master` and GitHub issues.
