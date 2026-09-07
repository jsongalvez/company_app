#!/usr/bin/env bash
# Static-analysis reproduction commands (#532, map #531 Phase A).
# Every command below runs the SAME committed config/profile CI uses:
#   Detekt : config/detekt/detekt.yml + detekt-anti-slop.yml (+ custom)
#   Kotlin : -PwarningsAsErrors=true (same flag as quality.yml)
#   IDE    : config/inspection/Project_Default.xml
# Full multi-platform Detekt/test coverage stays asynchronous CI work (#329).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

usage() {
    echo "usage: scripts/inspect.sh <detekt|compiler|parity|ide-profile|all>" >&2
    exit 1
}

# Authoritative Detekt pass over the modules this repo edits most.
cmd_detekt() {
    ./gradlew :backend:detekt :composeApp:detektDesktopMain \
        :composeApp:detektMetadataCommonMain :shared:detektJvmMain
}

# Authoritative compiler-warning pass (same -PwarningsAsErrors=true as CI).
cmd_compiler() {
    ./gradlew :backend:compileKotlin :composeApp:compileKotlinDesktop \
        :shared:compileKotlinJvm -PwarningsAsErrors=true
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
