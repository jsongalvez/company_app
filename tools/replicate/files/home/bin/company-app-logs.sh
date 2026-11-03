#!/usr/bin/env bash
# Keeps company_app's container logs across redeploys. Follows the running
# container and appends to $DIR/app.log; resumes by timestamp when the
# container is replaced. Rotates app.log at 50 MB and keeps 10 files (about
# 500 MB at most). Run by company-app-logs.service.
set -uo pipefail
. "$HOME/.config/company-app-deploy.env"
COOLIFY_APP_UUID="${LOG_APP_UUID:-$COOLIFY_APP_UUID}"
DIR="${LOG_DIR:-$HOME/logs/company_app}"; mkdir -p "$DIR"
LIVE="$DIR/app.log"; LAST="$DIR/.last"
MAX=$(( ${LOG_MAX_MB:-50} * 1024 * 1024 )); KEEP="${LOG_KEEP:-10}"
CHUNK="${LOG_CHUNK_S:-60}"

rotate() {
  [[ -f "$LIVE" && $(stat -c %s "$LIVE") -ge $MAX ]] || return 0
  for ((i=KEEP-1; i>=1; i--)); do [[ -f "$LIVE.$i" ]] && mv "$LIVE.$i" "$LIVE.$((i+1))"; done
  mv "$LIVE" "$LIVE.1"; rm -f "$LIVE.$((KEEP))" 2>/dev/null; rm -f "$LIVE".$((KEEP+1))
}

while :; do
  c=$(docker ps -q --filter "label=coolify.applicationUuid=$COOLIFY_APP_UUID" | head -1)
  if [[ -z "$c" ]]; then sleep 5; continue; fi
  last=$(cat "$LAST" 2>/dev/null || echo 1970-01-01T00:00:00Z)
  timeout "$CHUNK" docker logs -f --timestamps --since "$last" "$c" 2>&1 \
    | awk -v last="$last" '$1 > last' >> "$LIVE"
  t=$(tail -n 1 "$LIVE" 2>/dev/null | cut -d' ' -f1)
  [[ "$t" =~ ^20[0-9]{2}- ]] && echo "$t" > "$LAST"
  rotate
  sleep 1
done
