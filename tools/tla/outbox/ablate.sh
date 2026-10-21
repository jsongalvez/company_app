#!/usr/bin/env bash
# Prove each fix is needed: switch one fix off in the passing config and the model must fail.
# Usage: tools/tla/outbox/ablate.sh   (about 10 minutes)
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp=$(mktemp -d)
rc=0
check() { # flag invariant config
  sed "s/  $1 = TRUE/  $1 = FALSE/" "$here/$3" > "$tmp/ablate-$1.cfg"
  out=$("$here/run.sh" "$tmp/ablate-$1.cfg" 2>&1 | head -1)
  # run.sh wants "pass" for non today-* names: a failure there prints WRONG ... got fail, which is what we want here.
  if echo "$out" | grep -q "got fail"; then echo "ok    without $1 the model fails $2"; else echo "WRONG without $1 the model still passes $2: $out"; rc=1; fi
}
check DropNoopMark       NoSelfRefusal     fixed-NoSelfRefusal.cfg
check TriedKept          ConvergesToIntent fixed-ConvergesToIntent.cfg
check ChainVersions      NoSelfRefusal     fixed-NoSelfRefusal.cfg
check ReplayReason       NoSelfRefusal     fixed-NoSelfRefusal.cfg
check SkipRemoved        ConvergesToIntent fixed-ConvergesToIntent.cfg
check RecentUntilSeen    NoVanish          fixed-NoVanish.cfg
check BlockBehindFailed  NoSelfRefusal     noCapability-NoSelfRefusal.cfg
rm -rf "$tmp"
exit $rc
