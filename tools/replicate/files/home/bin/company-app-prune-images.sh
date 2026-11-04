#!/usr/bin/env bash
# Frees the root disk. Each deploy leaves ~0.8GB of build cache and an app image (most of its layers shared
# with the last one). Keeps the newest $KEEP app images (rollback), whatever image the app runs now, and the
# most recent $CACHE_KEEP of build cache (warm builds); removes the rest.
# Run hourly by company-app-prune-images.timer. DRY_RUN=1 prints what it would remove.
set -uo pipefail
. "$HOME/.config/company-app-deploy.env"   # COOLIFY_APP_UUID
KEEP="${KEEP:-3}"
CACHE_KEEP="${CACHE_KEEP:-2GB}"
run() { if [[ -n "${DRY_RUN:-}" ]]; then echo "would run: $*"; else "$@"; fi; }

before=$(df --output=avail -B1M / | tail -1)
live=$(docker ps --filter "label=coolify.applicationUuid=$COOLIFY_APP_UUID" --format '{{.Image}}')
docker images "$COOLIFY_APP_UUID" --format '{{.CreatedAt}}|{{.Repository}}:{{.Tag}}' | sort -r | cut -d'|' -f2 \
  | tail -n +"$((KEEP + 1))" | while read -r image; do
    grep -qxF "$image" <<<"$live" && continue
    if [[ -n "${DRY_RUN:-}" ]]; then echo "would remove $image"; else docker rmi "$image" >/dev/null && echo "removed $image"; fi
  done
run docker builder prune -af --keep-storage "$CACHE_KEEP" 2>/dev/null | tail -1
after=$(df --output=avail -B1M / | tail -1)
echo "root free: ${before}MB -> ${after}MB"
