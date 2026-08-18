# Gates — Build: reuse Javalin test server per route test class

- [x] G1: RouteValidationTest result records 65 passing tests with no failures or errors.
  CHECK: grep -E 'tests="65" skipped="0" failures="0" errors="0"' backend/build/test-results/test/TEST-com.companyb.companyapp.api.routes.RouteValidationTest.xml
  EXPECT: tests="65" skipped="0" failures="0" errors="0"
  EVIDENCE: <testsuite name="com.companyb.companyapp.api.routes.RouteValidationTest" tests="65" skipped="0" failures="0" errors="0" timestamp="2026-08-18T09:03:36.070Z" hostname="company-app-vps" time="46.167">
- [x] G2: Backend test-data cleanliness remains intact.
  CHECK: bash scripts/check-test-cleanliness.sh
  EXPECT: EXIT 0
  EVIDENCE: 17:10:25 [cleanliness] Checking test DB 'company_app_test' for leftover test data...
- [x] G3: Repository diff has no whitespace errors.
  CHECK: git diff --check
  EXPECT: EXIT 0
  EVIDENCE: exit 0
