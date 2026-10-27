#!/usr/bin/env bash
# Feeds perf-record a fixed log with known timings and checks the rows.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REC="$HERE/../perf-record"
REP="$HERE/../perf-report"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
export PERF_LOG="$T/app.log" PERF_DB="$T/perf.sqlite"
fails=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; fails=$((fails + 1)); }
check() { if [[ "$2" == "$3" ]]; then ok "$1"; else bad "$1: want [$3] got [$2]"; fi; }
q() { python3 -I -c 'import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);print(c.execute(sys.argv[2]).fetchone()[0])' "$PERF_DB" "$1"; }

line() { # ts template elapsed db trace
  printf '%s \033[0;39m \033[31m\033[0;39m%s [Jetty-1] \033[34m INFO\033[0;39m [TraceId-%s] +%s ms. \033[32mc.c.RequestLog\033[0;39m - \033[36m{"event":"request_completed","traceId":"%s","method":"GET","route":"/x","template":"%s","status":200,"elapsedMs":%s,"dbMs":%s,"queries":2}\n' \
    "$1" "$1" "$5" "$3" "$5" "$2" "$3" "$4"
}
A=/api/branches/{branchId}/today-screen
B=/api/sessions

# bucket 10:00-10:05: A has 1..100 ms, B has 10 requests of 10..100 ms
: > "$PERF_LOG"
for i in $(seq 1 100); do line "2026-10-27T10:01:$(printf %02d $((i % 60))).000000000Z" "$A" "$i" "$((i / 2))" "a$i" >> "$PERF_LOG"; done
for i in $(seq 1 10); do line "2026-10-27T10:02:00.000000000Z" "$B" "$((i * 10))" 1 "b$i" >> "$PERF_LOG"; done
line "2026-10-27T10:06:00.000000000Z" "$A" 7 1 "late" >> "$PERF_LOG"

export PERF_NOW=2026-10-27T10:07:00Z
"$REC"; check "first run exits 0" "$?" 0
check "A count" "$(q "select count from raw where template='$A'")" 100
check "A p50" "$(q "select p50 from raw where template='$A'")" 50
check "A p95" "$(q "select p95 from raw where template='$A'")" 95
check "A p99" "$(q "select p99 from raw where template='$A'")" 99
check "A max" "$(q "select max_ms from raw where template='$A'")" 100
check "A slowest trace" "$(q "select slow_trace from raw where template='$A'")" a100
check "B p95" "$(q "select p95 from raw where template='$B'")" 100
check "open bucket not recorded" "$(q "select count(*) from raw")" 2

"$REC"; check "rerun exits 0" "$?" 0
check "rerun adds nothing" "$(q "select count(*) from raw")" 2
check "rerun keeps count" "$(q "select count from raw where template='$A'")" 100

export PERF_NOW=2026-10-27T10:11:00Z
"$REC"
check "closed bucket now recorded" "$(q "select count(*) from raw")" 3
"$REC"
check "second rerun adds nothing" "$(q "select count(*) from raw")" 3

rep="$("$REP" --since 1h)"
grep -q "today-screen" <<<"$rep" && ok "report lists the Today template" || bad "report lists the Today template: $rep"
grep -E "today-screen" <<<"$rep" | grep -q " 101 " && ok "report counts both buckets" || bad "report counts both buckets: $rep"

# truncated log restarts from offset 0
: > "$PERF_LOG"
line "2026-10-27T10:20:00.000000000Z" "$B" 5 1 "after-trunc" >> "$PERF_LOG"
export PERF_NOW=2026-10-27T10:30:00Z
"$REC"
check "truncated log restarts" "$(q "select count(*) from raw where template='$B'")" 2

# rotation: nothing counted twice or skipped
line "2026-10-27T10:31:00.000000000Z" "$B" 6 1 "pre-rot" >> "$PERF_LOG"
export PERF_NOW=2026-10-27T10:33:00Z
"$REC"
line "2026-10-27T10:33:30.000000000Z" "$B" 8 1 "rot-old" >> "$PERF_LOG"
mv "$PERF_LOG" "$PERF_LOG.1"
line "2026-10-27T10:34:00.000000000Z" "$B" 9 1 "rot-new" >> "$PERF_LOG"
export PERF_NOW=2026-10-27T10:45:00Z
"$REC"
check "rotation counts every line once" "$(q "select sum(count) from raw where template='$B' and bucket>='2026-10-27T10:30:00Z'")" 3
"$REC"
check "rotation rerun is a no-op" "$(q "select sum(count) from raw where template='$B' and bucket>='2026-10-27T10:30:00Z'")" 3

# 1,000 junk lines
for i in $(seq 1 1000); do echo "junk $i {not json} request_completed" >> "$PERF_LOG"; done
printf 'partial line with no newline' >> "$PERF_LOG"
before="$(q "select count(*) from raw")"
"$REC"; check "junk exits 0" "$?" 0
check "junk adds no rows" "$(q "select count(*) from raw")" "$before"

# prune: raw older than 30 days goes, daily stays
export PERF_NOW=2026-12-05T00:00:00Z
"$REC"
check "raw older than 30 days pruned" "$(q "select count(*) from raw")" 0
check "daily rows stay" "$(q "select count(*) from daily where template='$A'")" 1

[[ $fails -eq 0 ]] && echo "all ok" || { echo "$fails failed"; exit 1; }
