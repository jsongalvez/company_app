#!/usr/bin/env bash
# Phone alerts for company_app through ntfy. Run by company-app-alert.timer.
#  1. a Coolify deployment of the app finished 'failed' (one alert per deployment)
#  2. /health failed twice in a row (one alert), then one recovery message
#  3. a key route slow in two probe runs in a row (one alert), then one recovery message: check-latency.py
# State lives in $STATE; overrides below exist for the proofs.
set -uo pipefail
. "$HOME/.config/company-app-deploy.env"   # COOLIFY_TOKEN, COOLIFY_URL, COOLIFY_APP_UUID
. "$HOME/.config/company-app-alerts.env"   # NTFY_URL, NTFY_TOPIC
NTFY_TOPIC="${TEST_TOPIC:-$NTFY_TOPIC}"
STATE="${ALERT_STATE:-$HOME/.local/state/company-app-alerts}"; mkdir -p "$STATE"
MAX_AGE_H="${ALERT_MAX_AGE_H:-36}"

notify() { # title message priority tags
  curl -s -m15 -o /dev/null -H "Title: $1" -H "Priority: ${3:-default}" -H "Tags: ${4:-}" -d "$2" "$NTFY_URL/$NTFY_TOPIC" \
    || echo "ntfy send failed: $1" >&2
}

check_deploys() {
  local json
  json=$(curl -s -m20 -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_URL/api/v1/deployments/applications/$COOLIFY_APP_UUID") || return 0
  touch "$STATE/deploys-seen"
  python3 - "$json" "$MAX_AGE_H" <<'PY' | while IFS=$'\t' read -r id sha when url; do
import json, sys, datetime as dt
try: d = json.loads(sys.argv[1])["deployments"]
except Exception: sys.exit(0)
cut = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=float(sys.argv[2]))
for x in d:
    if x.get("status") != "failed" or not x.get("finished_at"): continue
    t = dt.datetime.fromisoformat(x["finished_at"].replace("Z", "+00:00"))
    if t >= cut: print(x["deployment_uuid"], x["commit"][:8], x["finished_at"], x["deployment_url"], sep="\t")
PY
    grep -qx "$id" "$STATE/deploys-seen" && continue
    notify "company_app deploy FAILED" "Coolify deployment $id of ${sha} failed at ${when}. The server keeps running the previous version. Log in Coolify: $url" high "rotating_light"
    echo "$id" >> "$STATE/deploys-seen"
    echo "alerted deploy $id $sha"
  done
}

health_url() {
  [[ -n "${HEALTH_URL:-}" ]] && { echo "$HEALTH_URL"; return; }
  local c ip
  c=$(docker ps -q --filter "label=coolify.applicationUuid=$COOLIFY_APP_UUID" | head -1)
  [[ -n "$c" ]] || { echo "http://container-not-found.invalid/health"; return; }
  ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | awk '{print $1}')
  echo "http://$ip:3023/health"
}

check_health() {
  local url n
  url=$(health_url)
  n=$(cat "$STATE/health-fails" 2>/dev/null || echo 0)
  if curl -sf -m5 "$url" | grep -q '"UP"'; then
    if [[ -e "$STATE/health-down" ]]; then
      notify "company_app is back" "/health answers UP again (after $n failed checks)." default "white_check_mark"
      rm -f "$STATE/health-down"; echo "alerted recovery"
    fi
    echo 0 > "$STATE/health-fails"
  else
    n=$((n+1)); echo "$n" > "$STATE/health-fails"
    if (( n >= 2 )) && [[ ! -e "$STATE/health-down" ]]; then
      notify "company_app is DOWN" "/health failed $n checks in a row ($url)." urgent "rotating_light"
      touch "$STATE/health-down"; echo "alerted down"
    fi
  fi
}

check_latency() { python3 -I "$HOME/.local/share/company-app-perf/check-latency.py"; }

case "${1:-all}" in
  deploys) check_deploys ;;
  health) check_health ;;
  latency) check_latency ;;
  all) check_deploys; check_health; check_latency ;;
esac
