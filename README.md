## Developer Setup

### Start here
- install ktlint plugin
- install detekt plugin
- install k6: [download the binary](https://grafana.com/docs/k6/latest/set-up/install-k6/#download-the-k6-binary) and place it in your `PATH`

#### One-Time Detekt Configuration
- Go to Settings → Tools → detekt → Configuration file(s)
- Click `+` and select `detekt.yml` from project root
- Click OK and restart IDE

### Setup docker
start docker `docker compose -f docker/docker-compose.yml up -d`
- stopping docker `docker compose -f docker/docker-compose.yml down -v`

### Ktlint Commands

```agsl
./gradlew ktlintCheck
./gradlew ktlintFormat
```

### OpenCode Web (Mobile Access)

Share sessions between PC and phone:

```bash
source ~/.bashrc
opencode web --hostname 0.0.0.0 --port 8080
```

Then access from:
- **PC browser**: `http://localhost:8080`
- **Phone browser**: `http://192.168.1.8:8080` (same Wi-Fi)
- **Tailscale**: use your Tailscale IP to restrict access to only your Tailnet

To attach a terminal TUI to the running server:

```bash
opencode attach http://localhost:8080
```

## Wayfinder Loop (Automated Wayfinder Chain)

Automates the wayfinder session chain: watches `docs/agents/` for new `wayfinder-*-handoff.md` files (each session's completion signal), spawns a fresh zero-context opencode2 session that reads the newest handoff and drives the next session per its instructions, and notifies you when the agent parks on a question or the chain breaks. Sessions are one-ticket-per-session, claim-first, per the handoff docs.

```bash
# First start (seed with the latest handoff and spawn immediately)
./scripts/wayfinder-loop.sh --bootstrap wayfinder-163-handoff.md

# Normal start / restart (resumes supervision of the running session)
./scripts/wayfinder-loop.sh
```

Run it in tmux so it survives your SSH sessions:

```bash
tmux new-session -d -s wayfinder-loop './scripts/wayfinder-loop.sh 2>&1 | tee -a .wayfinder-loop.tmux.log'
```

### Controls

| Action | Command |
|---|---|
| Watch the daemon log live | `tmux attach -t wayfinder-loop` |
| Session history | `cat .wayfinder-loop.log` |
| Stop the chain (running agent session survives) | `tmux kill-session -t wayfinder-loop` |
| Resume supervision after a stop/reboot | `tmux new-session -d -s wayfinder-loop './scripts/wayfinder-loop.sh'` |

### When the agent needs you

The agent asks via the question tool, parks, and you get a notification. To answer:

```bash
opencode2   # in the repo — pick the "wayfinder-loop" session from the session list
```

Type your answer; the agent resumes and the daemon keeps supervising. If the agent blocks on a permission request instead of a question, attach the TUI and approve it the same way.

### Phone push (ntfy)

Desktop notifications always fire; phone push fires when a topic is set. The topic lives in `.wayfinder-loop.env` (gitignored):

```bash
# .wayfinder-loop.env
WAYFINDER_NTFY_TOPIC=wf-<random-topic>
```

Subscribe your phone: install the ntfy app, then subscribe to the topic URL printed below (or add the topic name manually):

```
https://ntfy.sh/wf-ky1wf3r3vo
```

Env overrides: `WAYFINDER_NTFY_TOPIC` (phone push topic), `WAYFINDER_POLL_SECS` (doc poll interval, default 15), `WAYFINDER_DRY_RUN` (log transitions without spawning).

### Runtime files

- `.wayfinder-loop.state` — last processed handoff + active session id (gitignored)
- `.wayfinder-loop.log` — daemon history (gitignored)
