#!/usr/bin/env bash
# Restores a nightly backup into this box's database, then prints table and user counts.
# Replaces the database, so it refuses the prod VPS and asks before it starts. Stop the app first.
# telemetry_event comes back empty: nightly dumps leave its rows out.
# Usage: restore-latest.sh [NAME]   default: the newest backup in the bucket
set -euo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPLICATE_SOURCE_ONLY=1 . "$HERE/replicate.sh"
refuse_prod

RCLONE=~/.local/bin/rclone
DB=${DB:-company_app}
C=$(db_uuid)
[[ -n "$C" ]] || { echo "no Postgres in Coolify" >&2; exit 1; }
[[ -z "$(app_container)" ]] || { echo "stop the app in Coolify first" >&2; exit 1; }

set -a; . ~/.config/company-app-backup.env; set +a
export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare \
  RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
  RCLONE_CONFIG_R2_ENDPOINT="https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" \
  RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true RCLONE_CONFIG=/dev/null

name=${1:-$($RCLONE lsf "r2:$R2_BUCKET" --include 'company_app-*.dump.gpg' | sort | tail -1)}
[[ -n "$name" ]] || { echo "no backup in the bucket" >&2; exit 1; }
read -r -p "Replace database $DB on $(hostname) with $name? Type the hostname: " reply
[[ "$reply" == "$(hostname)" ]] || { echo "stopped" >&2; exit 1; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
pg() { docker exec -i "$C" sh -c "$1"; }
$RCLONE copyto "r2:$R2_BUCKET/$name" "$tmp/b.gpg"
gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 -d -o "$tmp/db.dump" "$tmp/b.gpg" 3<<<"$BACKUP_PASSPHRASE"

pg "dropdb -U \"\$POSTGRES_USER\" --maintenance-db=postgres --force --if-exists $DB"
pg "createdb -U \"\$POSTGRES_USER\" --maintenance-db=postgres $DB"
pg "pg_restore -U \"\$POSTGRES_USER\" -d $DB --no-owner --no-acl --exit-on-error" <"$tmp/db.dump"

tables=$(pg "psql -U \"\$POSTGRES_USER\" -d $DB -Atc \"select count(*) from pg_tables where schemaname = 'public'\"")
pg "psql -U \"\$POSTGRES_USER\" -d $DB -Atc 'select count(*) from app_user'" >"$tmp/users"
echo "ok: $name restored, $tables tables, $(cat "$tmp/users") users. Start the app, then run replicate.sh check."
