#!/usr/bin/env bash
# Installs the latency probe on the VPS: scripts into ~/.local/share/company-app-perf, perf-slow on PATH,
# the 5-minute probe timer, and the latency check inside ~/bin/company-app-alert.sh. Safe to rerun.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEST=$HOME/.local/share/company-app-perf
mkdir -p "$DEST" "$HOME/.local/bin" "$HOME/.config/systemd/user" "$HOME/logs/company_app" "$HOME/.local/state/company-app-perf"
install -m 0755 "$HERE/probe.sh" "$HERE/perf-slow" "$HERE/check-latency.py" "$DEST/"
install -m 0644 "$HERE/probe.mjs" "$HERE/limits.json" "$DEST/"
ln -sf "$DEST/perf-slow" "$HOME/.local/bin/perf-slow"
install -m 0644 "$HERE/systemd/company-app-probe.service" "$HERE/systemd/company-app-probe.timer" "$HOME/.config/systemd/user/"
if ! grep -q 'check-latency' "$HOME/bin/company-app-alert.sh"; then
  python3 -I - "$HOME/bin/company-app-alert.sh" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace('case "${1:-all}" in', '''check_latency() { python3 -I "$HOME/.local/share/company-app-perf/check-latency.py"; }

case "${1:-all}" in''')
s = s.replace("  health) check_health ;;\n", "  health) check_health ;;\n  latency) check_latency ;;\n")
s = s.replace("  all) check_deploys; check_health ;;", "  all) check_deploys; check_health; check_latency ;;")
s = s.replace("#  2. /health failed twice in a row (one alert), then one recovery message\n", "#  2. /health failed twice in a row (one alert), then one recovery message\n#  3. a key route slow in two probe runs in a row (one alert), then one recovery message: check-latency.py\n")
open(p, "w").write(s)
PY
fi
systemctl --user daemon-reload
systemctl --user enable --now company-app-probe.timer
echo "installed: $(systemctl --user is-active company-app-probe.timer) timer, perf-slow at $HOME/.local/bin/perf-slow"
