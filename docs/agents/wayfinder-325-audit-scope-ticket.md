## Question

Docs: make Map #180 schema coverage follow authoritative migration contents.

Replace stale V19-only references in the architecture audit contract with an
authoritative-directory rule and current migration coverage language. Preserve the
audit's C-09 scope and avoid enumerating a cache that will go stale again.

Acceptance:

- No audit contract claims schema review stops at V19.
- The migration directory is named as authoritative.
- Documentation checks and diff checks pass.
