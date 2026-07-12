#!/bin/bash
# Install the project's git hooks.
# Run this once after cloning the repo.

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
git config core.hooksPath .githooks
echo "Git hooks installed from .githooks/"
