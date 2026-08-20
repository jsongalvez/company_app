# Child: Detekt inventory and baseline evidence

Part of Map #180, through parent rollout issue #274.

## Question

What Detekt policy can this repository safely adopt, based on current evidence?

## Scope

- Record Detekt and Kotlin versions, applied plugins, config files, rule-set merge behavior, and existing findings.
- Enumerate actual Kotlin source sets and registered Detekt tasks for backend, shared, and Compose targets/tests from Gradle task output.
- Record current pre-commit, pre-push, and CI commands and their source-set coverage.
- Compare `config/detekt/detekt-anti-slop.yml` with `~/anti-slop-detekt` without changing enforcement.
- Produce a rule matrix covering rule, rationale, affected source sets, representative findings, false-positive risk, and disposition.

## Completion evidence

- No production, test, Gradle, Detekt, hook, or CI enforcement changes.
- Inventory cites command output and repository paths; no guessed iOS or test task names.
- Current findings are reproducible or explicitly marked unavailable with the failing command and cause.
- Every upstream deviation is classified as compatible, version/source-set limitation, verified false positive, or unresolved.
- Resolution identifies the smallest safe next child and any prerequisite blocker.
