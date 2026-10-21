#!/usr/bin/env bash
# Run the Outbox model. Usage: tools/tla/outbox/run.sh [config ...]   (default: every *.cfg here)
#   today-*.cfg   today's code: each one is EXPECTED to fail and prints the counterexample
#   fixed-*.cfg   all five fixes on: must pass
# Needs Java 17+. Fetches tla2tools.jar once into ~/.local/share/tla (override with TLA_JAR).
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
jar="${TLA_JAR:-$HOME/.local/share/tla/tla2tools.jar}"
if [ ! -f "$jar" ]; then
  mkdir -p "$(dirname "$jar")"
  curl -fsSL -o "$jar" https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar || exit 2
fi
cfgs=("$@"); [ ${#cfgs[@]} -eq 0 ] && cfgs=("$here"/*.cfg)
rc=0
for c in "${cfgs[@]}"; do
  name=$(basename "$c" .cfg)
  out=$(cd "$here" && java -XX:+UseParallelGC -cp "$jar" tlc2.TLC -nowarning -metadir "$(mktemp -d)" -workers 2 -deadlock -config "$c" Outbox.tla 2>&1)
  rm -f "$here"/Outbox_TTrace_*
  if echo "$out" | grep -q "Model checking completed. No error"; then
    got=pass
  elif echo "$out" | grep -q "is violated"; then
    got=fail
  else
    got=broken
  fi
  want=pass; case "$name" in today-*|partial-*) want=fail ;; esac
  states=$(echo "$out" | grep -oE "[0-9]+ distinct states found" | tail -1)
  if [ "$got" = "$want" ]; then echo "ok    $name ($got, $states)"; else echo "WRONG $name: wanted $want, got $got"; rc=1; fi
  if [ "$got" = fail ]; then echo "$out" | python3 -I "$here/trace.py"; fi
  [ "$got" = broken ] && echo "$out" | tail -15
done
exit $rc
