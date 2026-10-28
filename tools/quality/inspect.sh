#!/usr/bin/env bash
# Static-analysis reproduction commands (#532, map #531 Phase A).
# Every command below runs the SAME committed config/profile CI uses:
#   Detekt : config/detekt/detekt.yml + detekt-anti-slop.yml (+ custom)
#   Kotlin : warnings are errors in every build (build.gradle.kts)
#   IDE    : config/inspection/Project_Default.xml
# Full multi-platform Detekt/test coverage stays asynchronous CI work (#329).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

usage() {
    echo "usage: tools/quality/inspect.sh <detekt|compiler|parity|ide-profile|all>" >&2
    exit 1
}

# Authoritative Detekt pass over the modules this repo edits most.
cmd_detekt() {
    ./gradlew :backend:detekt
}

# Authoritative compiler-warning pass (warnings are errors in every build).
cmd_compiler() {
    ./gradlew :backend:compileKotlin
}

# Detekt effective-config parity guard (IDE single-file load == Gradle merge).
cmd_parity() {
    ./gradlew :backend:test --tests 'com.companyb.companyapp.architecture.DetektConfigParityTest'
}

# Install the repo-owned inspection profile into the git-ignored .idea dir.
cmd_ide_profile() {
    mkdir -p .idea/inspectionProfiles
    cp config/inspection/Project_Default.xml .idea/inspectionProfiles/Project_Default.xml
    echo "installed .idea/inspectionProfiles/Project_Default.xml"
}

case "${1:-}" in
    detekt) cmd_detekt ;;
    compiler) cmd_compiler ;;
    parity) cmd_parity ;;
    ide-profile) cmd_ide_profile ;;
    all) cmd_detekt; cmd_compiler; cmd_parity ;;
    *) usage ;;
esac
