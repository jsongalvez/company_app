# Child: shared Detekt enforcement

Part of parent #274; blocked by safety-policy child.

## Question

How should validated safety policy cover shared production and test source sets without relying on aggregate `NO-SOURCE` tasks?

## Acceptance

- Use only Gradle tasks proven to exist and analyze shared sources.
- Cover common, JVM, Android, iOS, and tests where supported; document unsupported coverage and create follow-up only when needed.
- Resolve findings in bounded batches without unrelated behavior changes.
- Add negative-control evidence, focused compile/Detekt/tests, and P1-P4 review with P5 if task/config seams change.
