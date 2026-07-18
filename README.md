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
