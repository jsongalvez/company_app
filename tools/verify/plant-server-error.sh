#!/usr/bin/env bash
# Plants one server 500 on the local verify copy (never prod): hides the branch table, asks for
# GET /api/start as owner, puts the table back, then polls ntfy for the server-error alert with that trace id.
# Start the copy with ntfy on a throwaway topic first:
#   NTFY_URL=https://ntfy.sh NTFY_TOPIC=companyapp-test-$(openssl rand -hex 6) tools/verify/verify.sh up
#   NTFY_URL=https://ntfy.sh NTFY_TOPIC=<same topic> tools/verify/plant-server-error.sh
# Exit 0 when the alert arrived, 1 when it did not.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PORT=${VERIFY_PORT:-18181}
DB=verify_$PORT
BASE=http://127.0.0.1:$PORT
: "${NTFY_URL:?}" "${NTFY_TOPIC:?}"
set -a; . "$ROOT/.env"; set +a
psql_v() { docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "${VERIFY_DB_CONTAINER:-company-app-devdb}" psql -U "$POSTGRES_USER" -d "$DB" -qtAc "$1"; }

token=$(curl -sf "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"username":"owner","password":"owner-dev-pass"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
since=$(date +%s)
psql_v 'alter table branch rename to branch_planted'
trap "psql_v 'alter table branch_planted rename to branch'" EXIT
trace=$(curl -s -o /dev/null -D - "$BASE/api/start" -H "Authorization: Bearer $token" | tr -d '\r' | awk -v IGNORECASE=1 '/^x-trace-id:/ {print $2} /^HTTP/ {print $2 > "/dev/stderr"}')
psql_v 'alter table branch_planted rename to branch'
trap - EXIT
echo "planted GET /api/start, trace $trace"
for _ in $(seq 1 12); do
  hit=$(curl -s "$NTFY_URL/$NTFY_TOPIC/json?poll=1&since=$since" | grep -F "$trace" || true)
  if [ -n "$hit" ]; then
    python3 -c 'import json,sys; m=json.loads(sys.stdin.readline()); print("ntfy alert: [%s] %s" % (m.get("title"), m["message"].replace("\n"," | ")))' <<<"$hit"
    exit 0
  fi
  sleep 5
done
echo "no ntfy alert with trace $trace within 60s"
exit 1
