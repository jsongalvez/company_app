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
