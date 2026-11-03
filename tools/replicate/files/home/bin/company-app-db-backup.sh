#!/usr/bin/env bash
# Nightly encrypted backup of the live company_app database to Cloudflare R2.
# Stays inside R2's free tier on purpose: if the bucket is over CAP_BYTES it
# refuses to upload (backups stop) instead of costing money.
set -euo pipefail

CAP_BYTES=$((2 * 1024 * 1024 * 1024))   # 2 GiB; R2 free tier is 10 GB
KEEP=90d
DB_CONTAINER=@DB_UUID@
RCLONE=~/.local/bin/rclone

set -a; . ~/.config/company-app-backup.env; set +a
export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare \
  RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
  RCLONE_CONFIG_R2_ENDPOINT="https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" \
  RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true RCLONE_CONFIG=/dev/null
DEST="r2:$R2_BUCKET"

# Monthly tally of R2 calls, so we stop far below the free 1M Class A /
# 10M Class B per month. Every rclone call logs its HTTP requests; listing,
# uploads and deletes count as Class A, plain reads as Class B.
MAX_A=100000; MAX_B=1000000
STATE=~/.local/state/company-app-backup; mkdir -p "$STATE"
TALLY="$STATE/ops-$(date +%Y-%m).txt"
[[ -f "$TALLY" ]] || echo "0 0" > "$TALLY"
read -r ops_a ops_b < "$TALLY"
if (( ops_a >= MAX_A || ops_b >= MAX_B )); then
  echo "REFUSED: this month already used $ops_a Class A / $ops_b Class B calls. No upload." >&2
  exit 3
fi
r2() {
  local log; log=$(mktemp)
  local rc=0
  $RCLONE "$@" --dump headers --log-file "$log" || rc=$?
  local reqs; reqs=$(grep -A1 "HTTP REQUEST" "$log" | grep -oE ': (GET|PUT|POST|HEAD|DELETE) [^ ]*' || true)
  local a b
  read -r ops_a ops_b < "$TALLY"   # re-read: size runs in a subshell
  a=$(grep -cE ': (PUT|POST|DELETE) |list-type=|[?&]uploads' <<<"$reqs" || true)
  b=$(( $(grep -cE ': (GET|HEAD) ' <<<"$reqs" || true) - $(grep -cE ': GET .*(list-type=|[?&]uploads)' <<<"$reqs" || true) ))
  ops_a=$((ops_a + a)); ops_b=$((ops_b + b)); echo "$ops_a $ops_b" > "$TALLY"
  rm -f "$log"; return $rc
}

used=$(r2 size --json "$DEST" | python3 -I -c 'import json,sys;print(json.load(sys.stdin)["bytes"])')
if (( used > CAP_BYTES )); then
  echo "REFUSED: bucket holds $used bytes, over the $CAP_BYTES cap. No upload." >&2
  exit 2
fi

name="company_app-$(date +%Y-%m-%d_%H%M).dump.gpg"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
docker exec "$DB_CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d company_app -Fc --exclude-table-data=telemetry_event' > "$tmp/db.dump"
[[ -s "$tmp/db.dump" ]] || { echo "empty dump" >&2; exit 1; }
gpg --batch --yes --pinentry-mode loopback --passphrase-fd 3 \
  --symmetric --cipher-algo AES256 -o "$tmp/$name" "$tmp/db.dump" 3<<<"$BACKUP_PASSPHRASE"
r2 copyto "$tmp/$name" "$DEST/$name"
r2 delete --min-age "$KEEP" "$DEST"
echo "uploaded $name ($(stat -c %s "$tmp/$name") bytes); bucket was $used bytes; calls this month: $ops_a A / $ops_b B"
