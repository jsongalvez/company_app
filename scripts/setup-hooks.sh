#!/bin/bash
# Install the project's git hooks.
# Run this once after cloning the repo.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$ROOT_DIR/scripts/lib/common.sh"

git config core.hooksPath .githooks
if [ ! -x "$ROOT_DIR/.githooks/commit-msg" ]; then
    echo "ERROR: .githooks/commit-msg is missing or not executable." >&2
    exit 1
fi
log setup-hooks "Git hooks installed from .githooks/"

# Install ktlint CLI (needed by pre-commit for staged-only formatting).
# Hooks never run Gradle (map #329), so without this CLI the hook warns and
# skips Kotlin formatting; asynchronous ktlintCheck in CI still catches debt.
KTLINT_VERSION="1.8.0"
KTLINT_DIR="$HOME/.cache/company-app/ktlint"
KTLINT_PATH="$KTLINT_DIR/$KTLINT_VERSION"
if [ -x "$KTLINT_PATH" ]; then
    echo "ktlint $KTLINT_VERSION already cached at $KTLINT_PATH"
elif command -v java &>/dev/null; then
    mkdir -p "$KTLINT_DIR"
    echo "Downloading ktlint $KTLINT_VERSION to $KTLINT_PATH..."
    curl -sSLo "$KTLINT_PATH" "https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint"
    chmod +x "$KTLINT_PATH"
    echo "ktlint $KTLINT_VERSION cached at $KTLINT_PATH."
else
    echo "WARNING: java not found — cannot run ktlint standalone jar."
    echo "Pre-commit will warn and skip Kotlin formatting; CI ktlintCheck still applies."
fi
