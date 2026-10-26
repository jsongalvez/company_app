#!/usr/bin/env bash
# One capacity run against a verify stack. Usage: run.sh <label> <vus> <think_min> <think_max> [hold_s]
# Samples loadavg, the app's java CPU, the database container, k6 and any other gradle/java build every 5s.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LABEL=${1:?label}; VUS=${2:?vus}; TMIN=${3:?}; TMAX=${4:?}; HOLD=${5:-300}
PORT=${VERIFY_PORT:-18185}
TOKENS=${TOKENS:-/tmp/capacity-tokens.json}
OUT=${OUT:-/tmp/capacity-out}; mkdir -p "$OUT"
APP_PID=$(ss -ltnp "sport = :$PORT" | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2)
[ -n "$APP_PID" ] || { echo "nothing listens on $PORT" >&2; exit 1; }
other_builds() { pgrep -af 'gradle|kotlin-compiler|npm|vite|tsc|playwright' | grep -v -E "pgrep|run.sh|:backend:run|$APP_PID" | wc -l; }
busy() { awk '/^cpu /{print $2+$3+$4+$7+$8, $2+$3+$4+$5+$6+$7+$8}' /proc/stat; }
# Wait for a quiet box: other agents' builds share these 2 cores. Give up after QUIET_WAIT seconds and say so.
read -r q0 qt0 < <(busy); quiet=0; waited=0
while [ "$quiet" -lt 4 ] && [ "$waited" -lt "${QUIET_WAIT:-900}" ]; do
  sleep 5; waited=$((waited + 5)); read -r q1 qt1 < <(busy)
  if [ $(( 100 * (q1 - q0) / (qt1 - qt0 + 1) )) -lt 30 ]; then quiet=$((quiet + 1)); else quiet=0; fi; q0=$q1; qt0=$qt1
done
echo "quiet_wait=${waited}s quiet_samples=$quiet"
echo "label=$LABEL vus=$VUS think=$TMIN-$TMAX hold=${HOLD}s app_pid=$APP_PID loadavg_before=$(cut -d' ' -f1-3 /proc/loadavg) other_builds_before=$(other_builds)"
DBCG=/sys/fs/cgroup/system.slice/docker-$(docker inspect -f '{{.Id}}' company-app-devdb).scope/cpu.stat
app_ms() { awk '{printf "%d", ($14 + $15) * 10}' "/proc/$APP_PID/stat"; }
db_ms() { awk '/^usage_usec/{printf "%d", $2 / 1000}' "$DBCG"; }
A0=$(app_ms); D0=$(db_ms); T0=$(date +%s%N)
SAMPLES="$OUT/$LABEL.samples"; : >"$SAMPLES"
(
  read -r b0 t0 < <(busy)
  while true; do
    sleep 3
    read -r b1 t1 < <(busy); box=$(( 100 * (b1 - b0) / (t1 - t0 + 1) )); b0=$b1; t0=$t1
    app=$(ps -o %cpu= -p "$APP_PID" 2>/dev/null | tr -d ' ')
    k6c=$(ps -o %cpu= -C k6 2>/dev/null | head -1 | tr -d ' ')
    db=$(docker stats --no-stream --format '{{.CPUPerc}}' company-app-devdb 2>/dev/null)
    prod=$(docker stats --no-stream --format '{{.CPUPerc}}' 8dtmibe0hpkf03t0bw1b0u0c-20261026T073350 2>/dev/null)
    pool=$(curl -s -m 2 "http://127.0.0.1:$PORT/metrics" | awk '/^hikaricp_connections_active /{a=$2} /^hikaricp_connections_pending /{p=$2} /^hikaricp_connections_total /{t=$2} END{print a "/" p "/" t}')
    echo "$(date +%T) pool=$pool box=$box% load=$(cut -d' ' -f1 /proc/loadavg) app=$app k6=$k6c db=$db prod=$prod other=$(other_builds)" >>"$SAMPLES"
  done
) &
SAMPLER=$!
/usr/bin/time -f "%U %S" -o "$OUT/$LABEL.k6time" k6 run --quiet -e TOKENS="$TOKENS" -e VUS="$VUS" -e HOLD_S="$HOLD" -e THINK_MIN="$TMIN" -e THINK_MAX="$TMAX" -e PROBE="${PROBE:-0}" \
  --summary-export "$OUT/$LABEL.json" "$HERE/capacity.js" >"$OUT/$LABEL.txt" 2>&1
RC=$?
A1=$(app_ms); D1=$(db_ms)
read -r KU KS < <(tail -1 "$OUT/$LABEL.k6time" 2>/dev/null) || { KU=0; KS=0; }
K6_MS=$(python3 -I -c "print(int(($KU + $KS) * 1000))")
export WALL_MS=$(( ($(date +%s%N) - T0) / 1000000 )) APP_MS=$((A1 - A0)) DB_MS=$((D1 - D0)) K6_MS
kill $SAMPLER 2>/dev/null; wait $SAMPLER 2>/dev/null
echo "k6 rc=$RC loadavg_after=$(cut -d' ' -f1-3 /proc/loadavg) other_builds_max=$(grep -o 'other=[0-9]*' "$SAMPLES" | cut -d= -f2 | sort -n | tail -1)"
python3 -I - "$OUT/$LABEL.json" "$SAMPLES" <<'PY'
import json, sys, re, os
d = json.load(open(sys.argv[1]))["metrics"]
w = float(os.environ["WALL_MS"])
def p(m, k): return round(d[m][k], 0) if m in d and k in d[m] else None
print("rps=%s reqs=%s failed=%.3f%% status_429=%s sessions_saved=%s" % (round(d["http_reqs"]["rate"], 1), d["http_reqs"]["count"], 100*d["failures"]["value"], d.get("status_429", {}).get("count", 0), d.get("sessions_saved", {}).get("count", 0)))
for m in ("today_view_ms", "save_session_ms", "finance_view_ms", "sign_in_ms"):
    print("%-16s n=%s p50=%s p95=%s p99=%s max=%s" % (m, d[m]["count"] if "count" in d[m] else "-", p(m, "med"), p(m, "p(95)"), p(m, "p(99)"), p(m, "max")))
# ps %cpu is a lifetime average, so the sampler's app, k6 and db columns are not reported; counters above are.
s = [dict(kv.split("=") for kv in l.split()[1:]) for l in open(sys.argv[2]) if l.strip()]
def pct(x): 
    try: return float(x.rstrip("%"))
    except: return 0.0
for r in s:
    r["foreign"] = "%d%%" % max(0, 2 * pct(r["box"]) - float(os.environ["APP_MS"]) / w * 100 - float(os.environ["K6_MS"]) / w * 100 - float(os.environ["DB_MS"]) / w * 100)
for key in ("box", "prod", "foreign"):
    v = [pct(r.get(key, "0")) for r in s]
    if v: print("cpu %-4s mean=%.0f%% max=%.0f%%" % (key, sum(v)/len(v), max(v)))
n = d["http_reqs"]["count"]
print("cpu-ms per request: app=%.2f db=%.2f k6=%.2f (checked: /proc and cgroup counters; app = whole java process incl. JIT and GC)" % (float(os.environ["APP_MS"])/n, float(os.environ["DB_MS"])/n, float(os.environ["K6_MS"])/n))
print("avg cores used over the run (checked, counters): app=%.2f db=%.2f k6=%.2f" % (float(os.environ["APP_MS"])/w, float(os.environ["DB_MS"])/w, float(os.environ["K6_MS"])/w))
print("(cpu in %-of-one-core except box, which is %-of-both-cores; foreign = box*2 - app - k6 - db: other processes on the machine)")
pa = [tuple(float(x) for x in r["pool"].split("/")) for r in s if r.get("pool", "").replace("/", "").isdigit() and r["pool"].count("/") == 2]
if pa: print("db pool, sampled every ~3s: open connections max=%.0f, active mean=%.1f max=%.0f, waiting-for-a-connection max=%.0f" % (max(t for _, _, t in pa), sum(a for a, _, _ in pa)/len(pa), max(a for a, _, _ in pa), max(p for _, p, _ in pa)))
print("loadavg max=%.1f" % max(float(r["load"]) for r in s))
PY
