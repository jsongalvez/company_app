## Question

Make test teardown deletion order match schema foreign keys. Correct `BasePostgresTest.FK_GRAPH` for inventory movements, notifications, and medical mission delegates, including all actual parent tables and no unrelated parents. Add a schema-backed regression proving tracked rows clean without FK failures. Keep production schema and runtime behavior unchanged.

## Validation

- Verify graph edges against the authoritative Flyway schema and Exposed models.
- Add focused teardown coverage, then run backend tests, cleanliness checks, and the standard review profile without parallel test database use.
