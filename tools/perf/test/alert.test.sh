#!/usr/bin/env bash
# Feeds fixed probe rows and app-log lines to check-latency.py and checks which phone alerts it sends.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CHECK="$HERE/../check-latency.py"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
export PROBE_LOG="$T/probe.jsonl" APP_LOG="$T/app.log" ALERT_STATE="$T/state" LIMITS="$HERE/../limits.json"
export NOW="2026-10-27T10:00:00Z" NOTIFY_CMD="$T/notify.sh"
cat >"$T/notify.sh" <<'N'
#!/usr/bin/env bash
printf '%s | %s\n' "$1" "$2" >>"$SENT"
N
chmod +x "$T/notify.sh"
export SENT="$T/sent"
fails=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; fails=$((fails + 1)); }

row() { # minute route ok db app trace
  printf '{"ts":"2026-10-27T09:%02d:00Z","route":"%s","ok":%s,"clientMs":%d,"dbMs":%d,"appMs":%d,"trace":"%s"}\n' "$1" "$2" "$3" "$(($4 + $5 + 5))" "$4" "$5" "$6" >>"$PROBE_LOG"
}
run() { python3 -I "$CHECK" >"$T/out" 2>&1; local rc=$?; [ $rc = 0 ] || { echo "check exited $rc"; cat "$T/out"; }; return 0; }
sent() { [ -f "$SENT" ] && wc -l <"$SENT" | tr -d ' ' || echo 0; }
expect() { # label count
  [ "$(sent)" = "$2" ] && ok "$1" || { bad "$1 (sent $(sent), want $2)"; cat "$SENT" 2>/dev/null; }
}
reset() { : >"$PROBE_LOG"; rm -f "$SENT" "$APP_LOG"; rm -rf "$ALERT_STATE"; }

reset; row 40 today true 8 20 FAST1; row 45 today true 12 640 SLOW1; run
expect "one slow run sends nothing" 0

row 50 today true 12 690 SLOW2; run
expect "two slow runs send one alert" 1
grep -q "Today slow" "$SENT" && grep -q "perf-slow SLOW2" "$SENT" && grep -q "690ms in code, 12ms in database" "$SENT" && grep -q "300ms" "$SENT" \
  && ok "alert names the route, the limit, the slowest trace and the code share" || { bad "alert text"; cat "$SENT"; }

row 55 today true 12 700 SLOW3; run
expect "a third slow run does not repeat the alert" 1

row 58 today true 9 20 FAST2; run
expect "one fast run is not a recovery yet" 1
row 59 today true 9 21 FAST3; run
expect "two fast runs send one recovery" 2
grep -q "Today is back" "$SENT" && ok "recovery message" || { bad "recovery text"; cat "$SENT"; }
run; expect "nothing repeats after recovery" 2

reset; row 40 save true 10 20 A; row 45 save true 10 900 B; row 50 save true 10 20 C; run
expect "slow then fast does not alert" 0

reset; row 50 save true 700 10 D1; row 55 save true 690 10 D2; run
expect "database-heavy breach alerts" 1
grep -q "10ms in code, 700ms in database" "$SENT" && ok "names the database" || { bad "database wording"; cat "$SENT"; }

reset; row 40 today false 0 0 X; row 45 today false 0 0 Y; row 50 today false 0 0 Z; run
expect "failed probe runs (app down) stay quiet" 0

reset; row 50 finance true 5 1500 F1; row 55 finance true 5 1500 F2; row 50 signin true 400 200 S1; row 55 signin true 400 200 S2; run
expect "only the slow route alerts" 1
grep -q "Finance slow" "$SENT" && ok "finance named" || { bad "finance wording"; cat "$SENT"; }

reset; row 41 today true 8 640 OLD1; row 45 today true 8 650 OLD2; row 50 today false 0 0 DOWN; run
expect "slow runs followed by a failed run (app down) stay quiet" 0

reset
python3 -I - "$APP_LOG" <<'PY'
import sys
with open(sys.argv[1], "w") as f:
    for i in range(30):
        ms = 900 if i >= 2 else 100
        f.write('2026-10-27T09:%02d:00.100Z [x] INFO RequestLog - {"event":"request_completed","traceId":"R%d","method":"GET","route":"/api/branches/b/today-screen","template":"/api/branches/{branchId}/today-screen","status":200,"elapsedMs":%d,"dbMs":5,"queries":3}\n' % (20 + i // 2, i, ms))
PY
run
expect "real traffic p95 over the limit with 20+ requests alerts" 1
grep -q "real requests" "$SENT" && ok "says real requests" || { bad "real wording"; cat "$SENT"; }

reset
python3 -I - "$APP_LOG" <<'PY'
import sys
with open(sys.argv[1], "w") as f:
    for i in range(30):
        f.write('2026-10-27T09:%02d:00.100Z [x] INFO RequestLog - {"event":"request_completed","traceId":"R%d","method":"POST","route":"/auth/login","template":"/auth/login","status":200,"elapsedMs":3400,"dbMs":5,"queries":3}\n' % (20 + i // 2, i))
PY
run
expect "slow real sign-ins alert once" 1
row 55 signin true 20 200 OK1; row 57 signin true 20 200 OK2; run
expect "good probe runs alone do not clear a breach seen in real traffic" 1
python3 -I - "$APP_LOG" <<'PY'
import sys
with open(sys.argv[1], "a") as f:
    for i in range(25):
        f.write('2026-10-27T10:%02d:00.100Z [x] INFO RequestLog - {"event":"request_completed","traceId":"Q%d","method":"POST","route":"/auth/login","template":"/auth/login","status":200,"elapsedMs":300,"dbMs":5,"queries":3}\n' % (10 + i // 2, i))
PY
printf '{"ts":"2026-10-27T10:20:00Z","route":"signin","ok":true,"clientMs":9,"dbMs":20,"appMs":200,"trace":"P1"}\n{"ts":"2026-10-27T10:25:00Z","route":"signin","ok":true,"clientMs":9,"dbMs":20,"appMs":200,"trace":"P2"}\n' >>"$PROBE_LOG"
NOW="2026-10-27T10:30:00Z" run
expect "20 fresh fast requests clear it with one recovery" 2
NOW="2026-10-27T10:31:00Z" run
expect "and nothing repeats" 2

# Sign-in answers anonymous callers without Server-Timing, so its probe rows carry only the client time.
reset
printf '{"ts":"2026-10-27T09:50:00Z","route":"signin","ok":true,"clientMs":%d,"dbMs":null,"appMs":null,"trace":"%s"}\n' 3100 NS1 >>"$PROBE_LOG"; run
expect "one slow sign-in without a server split sends nothing" 0
printf '{"ts":"2026-10-27T09:55:00Z","route":"signin","ok":true,"clientMs":%d,"dbMs":null,"appMs":null,"trace":"%s"}\n' 3300 NS2 >>"$PROBE_LOG"; run
expect "two slow sign-ins without a server split alert once" 1
grep -q "3300ms" "$SENT" && grep -q "perf-slow NS2" "$SENT" && ok "it names the client time and trace" || { bad "no-split alert text"; cat "$SENT"; }

[ "$fails" = 0 ] && echo "all alert tests passed" || { echo "$fails failed"; exit 1; }
