#!/usr/bin/env bash
# Installs perf-record, perf-report and the 5-minute timer on the host. Safe to rerun.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HOME/.local/bin" "$HOME/.config/systemd/user"
install -m 755 "$HERE/perf-record" "$HERE/perf-report" "$HOME/.local/bin/"
install -m 644 "$HERE/company-app-perf.service" "$HERE/company-app-perf.timer" "$HOME/.config/systemd/user/"
systemctl --user daemon-reload
systemctl --user enable --now company-app-perf.timer
systemctl --user list-timers company-app-perf.timer --no-pager
