#!/usr/bin/env bash
# Self-test for tools/replicate: the check/plan/apply loop, names-only env reporting, and shipped files.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
R=$ROOT/tools/replicate/replicate.sh
fails=0
ok() { # label, command...
  local label=$1; shift
  if "$@"; then echo "ok: $label"; else echo "FAIL: $label"; fails=$((fails + 1)); fi
}
for f in "$R" "$ROOT"/tools/replicate/*.sh; do ok "parses $(basename "$f")" bash -n "$f"; done

t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
cat >"$t/h.sh" <<H
REPLICATE_SOURCE_ONLY=1 . "$R"
hostname() { echo new-box; }
ip() { :; }
ITEMS=("good|agent|g" "broken|agent|b" "soft|note|s")
chk_good() { return 0; }
chk_soft() { return 2; }
chk_broken() { [[ -e "$t/fixed" ]]; }
fix_broken() { touch "$t/fixed"; echo fixed >>"$t/fixes"; }
H
run() { bash -c ". '$t/h.sh'; main $*"; }
quiet() { "$@" >/dev/null 2>&1; }

expect_rc() { local want=$1; shift; quiet "$@"; [[ $? == "$want" ]]; }
ok "check fails on a failing item" expect_rc 1 run check
ok "plan changes nothing" expect_rc 0 run plan
ok "plan ran no fix" test ! -e "$t/fixes"
ok "apply fixes and rechecks" expect_rc 0 run apply
ok "check passes with a warning" expect_rc 0 run check
ok "strict fails on a warning" expect_rc 1 run check --strict
quiet run apply
ok "apply is idempotent" test "$(wc -l <"$t/fixes")" = 1

cat >>"$t/h.sh" <<H
chk_broken() { return 1; }
fix_broken() { :; }
H
ok "apply stops at a stuck item" expect_rc 1 run apply
ok "apply refuses the prod VPS" expect_rc 3 bash -c "REPLICATE_SOURCE_ONLY=1 . '$R'; hostname() { echo company-app-vps; }; main apply"

mkdir -p "$t/home/.config"
for n in alerts backup deploy probe; do (umask 077; echo "NTFY_URL=s3cr3t-value" >"$t/home/.config/company-app-$n.env"); done
out=$(HOME="$t/home" bash -c "REPLICATE_SOURCE_ONLY=1 . '$R'; chk_env_files; echo \"rc=\$? \$DETAIL\"")
ok "missing env names are reported" grep -q 'rc=1 missing:.*BACKUP_PASSPHRASE' <<<"$out"
ok "env values never print" bash -c "! grep -q s3cr3t <<<'$out'"

chk_lock() { bash -c "REPLICATE_SOURCE_ONLY=1 . '$R'; S=''; iptables() { printf '%s' '$1'; }; ss() { echo listening; }; chk_ports_lock"; }
ok "unlocked listening ports warn" expect_rc 2 chk_lock ''
all=$(printf -- '-A DOCKER-USER -i e -p tcp -m conntrack --ctorigdstport %s --ctdir ORIGINAL -j DROP\n' 8000 8080 6001 6002)
ok "locked ports pass" expect_rc 0 chk_lock "$all"

rendered=$(bash -c "REPLICATE_SOURCE_ONLY=1 . '$R'; REPLICA_DB_UUID=abcdefghij; REPLICA_APP_UUID=zyxwvutsrq; render '$ROOT/tools/replicate/files/home/.local/bin/app-shipcheck'; render '$ROOT/tools/replicate/files/home/bin/company-app-db-backup.sh'")
ok "render fills every field" bash -c "! grep -q '@[A-Z_]*@' <<<\"\$1\"" _ "$rendered"
ok "render uses the box's ids" grep -q 'DB_CONTAINER=abcdefghij' <<<"$rendered"
ok "no prod ids in shipped files" bash -c "! grep -rqE '0jpjzfk|8dtmibe0|BEGIN .*PRIVATE KEY' '$ROOT/tools/replicate/files'"

((fails == 0)) || { echo "replicate-test: $fails failed"; exit 1; }
echo "replicate-test: passed"
