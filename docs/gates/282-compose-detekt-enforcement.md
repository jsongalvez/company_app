# Gate Ledger: Compose Production Detekt Enforcement

## Scope

Child #282 wires every existing Compose production Detekt task into local and CI
quality paths. Generated Compose resources are excluded at the task boundary;
human-authored findings remain fail-closed.

## Evidence

- `:composeApp:detektMetadataCommonMain`: task exists; fails on 296 existing
  human-authored findings, primarily complexity/style findings.
- `:composeApp:detektAndroidDebug`: task exists; fails on 14 existing
  human-authored findings.
- `:composeApp:detektDesktopMain`: task exists; fails on 24 existing
  human-authored findings.
- `:composeApp:detektIosArm64Main`: task exists and passes.
- `:composeApp:detektIosSimulatorArm64Main`: task exists and is `NO-SOURCE`.
- Generated-resource findings are not suppressed by policy; task-level generated
  path exclusion targets only derived files.

## Disposition

The rollout remains fail-closed. No baseline, broad rule exclusion, or
per-file suppression was added. Existing human-authored complexity/style debt is
owned by ordered closeout child #281 and must be resolved or narrowly adjudicated
there before these mandatory tasks can pass.
