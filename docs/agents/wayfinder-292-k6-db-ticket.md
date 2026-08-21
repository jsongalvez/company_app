## Question

Make pre-push test-database cleanup use the exact database selected for k6 when
`TEST_DB_NAME` is absent. `.githooks/pre-push` defaults k6 to `company_app_test`
and exports it as `POSTGRES_DB`, while `clean-test-db.sh` and
`check-test-cleanliness.sh` derive `${POSTGRES_DB}_test`, producing
`company_app_test_test`.

Preserve explicit `TEST_DB_NAME` behavior. Add deterministic shell coverage for
unset `TEST_DB_NAME`, and keep production database safety and fail-closed cleanup
semantics intact.
