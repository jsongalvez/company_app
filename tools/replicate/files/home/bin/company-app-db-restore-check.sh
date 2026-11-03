#!/usr/bin/env bash
# Proves the newest nightly backup in R2 restores: download, decrypt, restore into a
# scratch database next to prod, and compare every table's row count with live
# (telemetry_event is left out of the backup's data, so it is skipped).
set -euo pipefail
DB_CONTAINER=@DB_UUID@
RCLONE=~/.local/bin/rclone
set -a; . ~/.config/company-app-backup.env; set +a
export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare \
  RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
  RCLONE_CONFIG_R2_ENDPOINT="https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" \
  RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true RCLONE_CONFIG=/dev/null

name=$($RCLONE lsf "r2:$R2_BUCKET" --include 'company_app-*.dump.gpg' | sort | tail -1)
[[ -n "$name" ]] || { echo "no backup in the bucket" >&2; exit 1; }
tmp=$(mktemp -d); scratch="r2_restore_$$"
pg() { docker exec -i "$DB_CONTAINER" sh -c "$1"; }
trap 'rm -rf "$tmp"; pg "dropdb -U \"\$POSTGRES_USER\" --if-exists $scratch" >/dev/null 2>&1 || true' EXIT
$RCLONE copyto "r2:$R2_BUCKET/$name" "$tmp/b.gpg"
gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 -d -o "$tmp/db.dump" "$tmp/b.gpg" 3<<<"$BACKUP_PASSPHRASE"
pg "createdb -U \"\$POSTGRES_USER\" $scratch"
pg "pg_restore -U \"\$POSTGRES_USER\" -d $scratch --no-owner --no-acl --no-tablespaces --exit-on-error" <"$tmp/db.dump"

counts() {
  pg "psql -U \"\$POSTGRES_USER\" -d $1 -Atq" <<'SQL'
DO $$ DECLARE t text; n bigint; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'telemetry_event' ORDER BY 1 LOOP
    EXECUTE format('SELECT count(*) FROM public.%I', t) INTO n;
    RAISE NOTICE '%|%', t, n;
  END LOOP; END $$;
SQL
}
live=$(counts company_app 2>&1); restored=$(counts "$scratch" 2>&1)
if [[ "$live" != "$restored" ]]; then diff <(echo "$live") <(echo "$restored") || true; echo "MISMATCH: $name" >&2; exit 1; fi
echo "ok: $name restores; $(wc -l <<<"$live") tables match live"
