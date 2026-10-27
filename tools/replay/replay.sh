#!/usr/bin/env bash
# Replay what led to a problem report, against a local copy of the database with personal details replaced.
#   replay.sh <feedback id prefix | telemetry session id> [--pii] [--keep] [--source prod|local:DBNAME]
# 1. finds the report and the page load that sent it (its "Telemetry: <id>" line, else the person's
#    last page load in the 30 minutes before it), and exports that page load's events;
# 2. copies the source database (telemetry rows left out), starts it on its own port with this checkout's
#    code (tools/verify/verify.sh), and replaces names, contacts, health notes and free text (redact.sql);
# 3. plays the events back in a browser signed in as the reporter (replay.mjs) and prints a verdict,
#    the call-by-call comparison and screenshots.
# --pii skips step 2's redaction. Use it only when the problem needs the real details, and say so.
# Without recorded events (reports from before telemetry) it opens the reported screen at the reported size.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
HERE="$ROOT/tools/replay"
ID=${1:?usage: replay.sh <feedback id | session id> [--pii] [--keep] [--source prod|local:DB]}
shift
PII=0 KEEP=0 SOURCE=prod
while [ $# -gt 0 ]; do
  case "$1" in
    --pii) PII=1 ;;
    --keep) KEEP=1 ;;
    --source) SOURCE=$2; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
case "$ID" in *[!0-9a-f-]*) echo "id must be hex" >&2; exit 2 ;; esac
export VERIFY_PORT=${REPLAY_PORT:-18192}
OUT=${REPLAY_OUT:-/tmp/replay}/$ID-$(date +%Y%m%dT%H%M%S)
mkdir -p "$OUT"

set -a; for f in "$ROOT/.env" "$(dirname "$(git -C "$ROOT" rev-parse --git-common-dir)")/.env"; do [ -f "$f" ] && { . "$f"; break; }; done; set +a
export PGPASSWORD=${POSTGRES_PASSWORD:-}
if [ "$SOURCE" = prod ]; then
  C=$(docker ps --format '{{.Names}}' | grep '^0jpjzfk' | head -1)
  [ -n "$C" ] || { echo "prod postgres container (0jpjzfk*) not running" >&2; exit 2; }
  q() { docker exec "$C" psql -U company_user -d company_app -AtX -c "$1"; }
  dump() { docker exec "$C" pg_dump -U company_user -d company_app -Fc --exclude-table-data=telemetry_event; }
else
  SRC_DB=${SOURCE#local:}
  q() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$SRC_DB" -AtX -c "$1"; }
  dump() { docker exec -e PGPASSWORD="$PGPASSWORD" company-app-devdb pg_dump -U "$POSTGRES_USER" -d "$SRC_DB" -Fc --exclude-table-data=telemetry_event; }
fi

FB=$(q "select id, user_id, created_at, coalesce(page,'#/'), coalesce(app_version,''), coalesce(substring(details from 'Telemetry: ([0-9a-f-]{36})'),''), coalesce(substring(details from 'Screen: ([0-9]+)'),''), coalesce(substring(details from 'Screen: [0-9]+.([0-9]+)'),'') from user_feedback where id::text like '$ID%' limit 1")
if [ -n "$FB" ]; then
  IFS='|' read -r FID USER_ID AT PAGE VERSION SID W H <<<"$FB"
  echo "report $FID at $AT on $PAGE (app $VERSION): $(q "select replace(message, E'\n', ' ') from user_feedback where id = '$FID'")"
  [ -n "$SID" ] || SID=$(q "select session_id from telemetry_event where user_id = '$USER_ID' and at between timestamptz '$AT' - interval '30 minutes' and timestamptz '$AT' + interval '1 minute' order by at desc limit 1" 2>/dev/null)
else
  SID=$ID
  USER_ID=$(q "select user_id from telemetry_event where session_id::text = '$ID' limit 1")
  [ -n "$USER_ID" ] || { echo "no report or page load with id $ID" >&2; exit 2; }
  PAGE='#/' W='' H=''
fi
if [ -n "$SID" ] && [ "$(q "select count(*) from telemetry_event where session_id = '$SID'" 2>/dev/null)" -gt 0 ] 2>/dev/null; then
  q "select coalesce(json_agg(json_build_object('seq', seq, 'kind', kind, 'data', data) order by seq), '[]') from telemetry_event where session_id = '$SID'" >"$OUT/events.json"
  echo "page load $SID: $(q "select string_agg(kind || ' ' || n, ', ') from (select kind, count(*) n from telemetry_event where session_id = '$SID' group by 1 order by 1) k")"
else
  echo "no recorded events (report predates telemetry or none were uploaded): opening $PAGE at ${W:-1280}x${H:-800}"
  printf '[{"seq":0,"kind":"boot","data":{"r":"%s","w":%s,"h":%s}}]\n' "$PAGE" "${W:-1280}" "${H:-800}" >"$OUT/events.json"
fi

echo "copying the database..."
dump >"$OUT/db.dump" || { echo "dump failed" >&2; exit 1; }
"$ROOT/tools/verify/verify.sh" down >/dev/null 2>&1
VERIFY_DUMP="$OUT/db.dump" "$ROOT/tools/verify/verify.sh" up || exit 1
rm -f "$OUT/db.dump"
REPLAY_DB=verify_$VERIFY_PORT
if [ "$PII" = 1 ]; then
  echo "WARNING: replaying WITH personal details (--pii); the copy keeps real names and notes until 'verify.sh down'"
  USERNAME=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$REPLAY_DB" -AtXc "select username from app_user where id = '$USER_ID'")
  echo "signing in as the reporter needs their password; with --pii it is set to the dev owner's in this copy"
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$REPLAY_DB" -qAtXc "update app_user set password_hash = (select password_hash from app_user where username = 'owner') where id = '$USER_ID'"
else
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$REPLAY_DB" -q -f "$HERE/redact.sql" || exit 1
  USERNAME=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$REPLAY_DB" -AtXc "select username from app_user where id = '$USER_ID'")
  echo "personal details replaced (redact.sql); signing in as $USERNAME"
fi

NODE=$(command -v node); [ "$("$NODE" -p 'process.versions.node.split(".")[0]')" -ge 24 ] || NODE=$HOME/.local/node/bin/node
"$NODE" "$HERE/replay.mjs" --base "http://127.0.0.1:$VERIFY_PORT" --events "$OUT/events.json" --user "$USERNAME" --pass owner-dev-pass --out "$OUT"
rc=$?
[ "$KEEP" = 1 ] && echo "kept running on http://127.0.0.1:$VERIFY_PORT (sign in as $USERNAME / owner-dev-pass); stop with VERIFY_PORT=$VERIFY_PORT tools/verify/verify.sh down" \
  || "$ROOT/tools/verify/verify.sh" down >/dev/null
exit $rc
