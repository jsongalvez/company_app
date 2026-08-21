# Child: Detekt safety policy configuration

Part of parent #274; blocked by completed inventory child #275.

## Question

Which upstream anti-slop safety rules are compatible with this repository's actual Detekt tasks, and what smallest configuration change enables only evidence-backed safety enforcement?

## Acceptance

- Compare every enabled upstream safety rule with #275 evidence and actual task/source-set support.
- Keep safety rules active for production and tests where supported.
- Record exact upstream section, compatibility evidence, false-positive disposition, and every deviation.
- Separate complexity/style cleanup from safety rules.
- Add no broad exclusions, silent baseline, file-wide suppressions, or regex substitute for unsupported type-resolution rules.
- Add a fail-red negative-control gate before configuration edits and run focused Detekt/config validation.
