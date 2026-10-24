#!/usr/bin/env bash
# DB-free self-test for tools/push.sh: a failing gate stops the push and the remote stays
# where it was; a passing gate pushes; arguments reach `git push`.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
status=0

git init -q --bare "$T/remote.git"
git init -q "$T/work"
cd "$T/work"
git config user.email t@example.com; git config user.name t
mkdir tools
cp "$ROOT/tools/push.sh" tools/push.sh
printf '#!/usr/bin/env bash\nexit 1\n' > tools/check-before-push.sh
chmod +x tools/*.sh
git add -A; git commit -q -m one
git remote add origin "$T/remote.git"
git branch -M master

rc=0; tools/push.sh origin master >/dev/null 2>&1 || rc=$?
[ "$rc" -ne 0 ] || { echo "push-test: push succeeded although the gate failed" >&2; status=1; }
[ -z "$(git ls-remote "$T/remote.git" refs/heads/master)" ] || { echo "push-test: remote got master despite a failing gate" >&2; status=1; }

printf '#!/usr/bin/env bash\nexit 0\n' > tools/check-before-push.sh
tools/push.sh origin master >/dev/null 2>&1 || { echo "push-test: push failed although the gate passed" >&2; status=1; }
[ "$(git ls-remote "$T/remote.git" refs/heads/master | cut -f1)" = "$(git rev-parse HEAD)" ] || { echo "push-test: remote master is not HEAD after a passing gate" >&2; status=1; }

[ $status -eq 0 ] && echo "push-test: OK"
exit $status
