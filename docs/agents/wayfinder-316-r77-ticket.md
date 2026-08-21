# Build: reconcile stale OpenAPI route fingerprint

## Question

The generated OpenAPI route fingerprint is stale and blocks `publishOpenApiSpec`, pre-commit, and OpenAPI CI despite backend quality passing. Reconcile the committed fingerprint with current registered routes and add deterministic validation that prevents accidental stale-artifact claims.

## Scope

- Generated OpenAPI fingerprint artifact and its existing normalization/verification workflow.
- Focused deterministic fixture or script test for stale and matching fingerprints.
- Preserve route behavior and existing OpenAPI ownership; no route redesign.

## Acceptance

- Current source generates and verifies committed fingerprint successfully.
- Deliberately stale fingerprint fails closed with actionable output.
- Existing OpenAPI workflow and mandatory local gate consume same verified artifact.
