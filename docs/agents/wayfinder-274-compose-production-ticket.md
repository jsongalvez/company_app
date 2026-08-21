# Child: Compose production Detekt enforcement

Part of parent #274; blocked by backend enforcement child.

## Question

How should validated safety policy cover Compose common, Android, Desktop, and iOS production sources using actual task coverage?

## Acceptance

- Verify each task and source set from Gradle output; never guess iOS task names.
- Keep Desktop/Android/iOS platform boundaries and lifecycle/error behavior intact.
- Exclude only generated paths with narrow evidence.
- Run negative control, supported compilation/Detekt checks, Compose tests, and explicit evidence for externally blocked targets.
