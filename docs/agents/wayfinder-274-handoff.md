# Handoff - Rollout Parent #274

## Session outcome

- Rolled back broad anti-slop Detekt implementation after review exposed source-set coverage gaps, CI/local parity gaps, broad safety exclusions, and runtime behavior risk.
- No code, configuration, gate, or test changes remain from that attempt; this handoff is the only current working-tree change.
- Rewrote #274 as parent rollout ticket. It now requires small ordered child issues, evidence before implementation, negative-control gates, bounded cleanup, and P1-P5 review where applicable.
- Recorded configuration-fidelity decision on #274: `~/anti-slop-detekt` YAML is canonical baseline. Deviations require exact upstream comparison, compatibility/false-positive evidence, smallest adaptation, and documented disposition.
- #274 remains open under Map #180 with native parent link intact.

## Verification

- `git status --short`: only `docs/agents/wayfinder-274-handoff.md` modified by this handoff.
- Removed temporary files created by rolled-back attempt:
  - `docs/gates/274-anti-slop-detekt.md`
  - `config/detekt/detekt-test-style.yml`
  - `backend/src/main/kotlin/com/companyb/companyapp/repository/ExposedIlike.kt`
- No post-rollback build required; rollback restored repository state.
- Parent issue: https://github.com/jsongalvez/company_app/issues/274
- Fidelity decision: https://github.com/jsongalvez/company_app/issues/274#issuecomment-5350959474

## Priority

- #274 marked `ready-for-agent` so next session can create and claim rollout children.
- Parent is not implementation-ready as a direct coding ticket. First child must inventory current Detekt configuration, plugins, source sets, task graph, findings, and hook/CI commands without changing enforcement.

## Next-session instructions

1. Load Map #180, #274, this handoff, `docs/agents/issue-tracker.md`, and applicable module guidance.
2. Verify #274 native parent link and query its open children before claiming work.
3. Create ordered child issues through `scripts/wayfinder-create-child.sh`; do not implement directly under #274.
4. Make inventory/evidence child first. Record actual Gradle tasks and findings; never guess iOS or test task names.
5. Keep upstream YAML close. Every deviation needs evidence and smallest-change rationale.
6. Add blocking edges between rollout children. Claim exactly one frontier child per session.
