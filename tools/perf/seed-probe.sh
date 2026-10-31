#!/usr/bin/env bash
# Creates the latency probe's hidden branch and user (seed-probe.sql). Safe to rerun: a second run changes nothing.
#   seed-probe.sh                       the prod database, through ~/.config/company-app-probe.env
#   PSQL="psql -h 127.0.0.1 -U u db" PROBE_PASS=... seed-probe.sh   any other database
# A database reset drops the probe's client too, so the client id the probe keeps is cleared when it is gone.
set -euo pipefail
HERE=$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)
ENV_FILE=${PROBE_ENV:-$HOME/.config/company-app-probe.env}
if [[ -z "${PSQL:-}" ]]; then
  . "$ENV_FILE"
  PSQL="docker exec -i $PROBE_DB_CONTAINER sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\"'"
fi
: "${PROBE_PASS:?PROBE_PASS is required}"
CLIENT_FILE=${PROBE_CLIENT_FILE:-$HOME/.local/state/company-app-perf/probe-client}
psql_run() { eval "$PSQL" -v ON_ERROR_STOP=1 -X -q "$@"; }

hash=$(printf '%s' "$PROBE_PASS" | python3 -I -c 'import bcrypt, sys; print(bcrypt.hashpw(sys.stdin.buffer.read(), bcrypt.gensalt(12)).decode())')
{ printf '\\set pass_hash %s\n' "'$hash'"; cat "$HERE/seed-probe.sql"; } | psql_run -At

client=$(cat "$CLIENT_FILE" 2>/dev/null || true)
if [[ -n "$client" ]]; then
  found=$(printf "SELECT count(*) FROM client WHERE id = '%s';\n" "$client" | psql_run -At)
  if [[ "$found" == 0 ]]; then : >"$CLIENT_FILE"; echo "probe client cleared: no longer in the database"; fi
fi
