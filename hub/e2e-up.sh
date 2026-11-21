#!/usr/bin/env bash
# Builds the relay's container and serves it on 127.0.0.1:$HUB_PORT as hub.kiel.party with a
# self-signed test cert, for web/tests/site-hub.e2e.ts (CI browser.yml and local runs).
#   hub/e2e-up.sh <app origin>...    e.g. hub/e2e-up.sh http://localhost:18080
#   hub/e2e-up.sh down               stop it and delete the test cert
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
NAME=${HUB_CONTAINER:-company-hub-e2e}
PORT=${HUB_PORT:-8443}
CERTS=${HUB_CERTS:-/tmp/company-hub-e2e-certs}

if [ "${1:-}" = down ]; then
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -rf "$CERTS"
  exit 0
fi
[ $# -ge 1 ] || { echo "usage: $0 <app origin>... | down" >&2; exit 2; }
origins=$(IFS=,; echo "$*")

mkdir -p "$CERTS"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 \
  -subj /CN=hub.kiel.party -addext subjectAltName=DNS:hub.kiel.party \
  -keyout "$CERTS/hub.key" -out "$CERTS/hub.crt" 2>/dev/null
chmod 644 "$CERTS/hub.key"
docker build -q -t company-hub:e2e "$HERE" >/dev/null
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -p "127.0.0.1:$PORT:443" -v "$CERTS:/certs:ro" company-hub:e2e -origins "$origins" >/dev/null
for _ in $(seq 1 30); do
  curl -sfk --resolve "hub.kiel.party:$PORT:127.0.0.1" "https://hub.kiel.party:$PORT/health" >/dev/null && { echo "hub up on 127.0.0.1:$PORT ($NAME)"; exit 0; }
  sleep 1
done
docker logs "$NAME"; exit 1
