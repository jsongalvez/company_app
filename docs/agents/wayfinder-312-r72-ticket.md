Part of #180

## Question

Make test-database count and cleanup operations use the shared host/container-aware `test_db_psql` transport so CI service PostgreSQL and local Docker fallback both work.

## Scope

- Replace direct `docker exec company-postgres psql` calls in cleanliness and cleanup scripts.
- Preserve output formats, fail-closed discovery, disposable test DB targeting, and existing cleanup behavior.
- Validate shell syntax and disposable host/container paths.
