# Child: local and CI Detekt parity

Part of parent #274; blocked by test enforcement child.

## Question

How can pre-commit and CI consume equivalent Detekt policy, source-set coverage, warning handling, and fail-closed behavior?

## Acceptance

- Compare local hooks, CI workflows, and Gradle task graph from source evidence.
- Make one policy/task set authoritative without duplicating rule configuration.
- Prove deliberate findings fail through each mandatory path and failures propagate.
- Preserve disposable test database safety and document external branch-protection follow-up without guessing it.
