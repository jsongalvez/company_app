# Gates - 299: Auth route ownership

- [x] G1: Auth route registrations use shared route constants
  CHECK: grep -nE 'routes\.post\("/auth/(login|register)"' backend/src/main/kotlin/com/companyb/companyapp/api/routes/AuthRoutes.kt
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: Auth route constants remain byte-equivalent
  CHECK: grep -q 'const val AUTH_LOGIN = "/auth/login"' shared/src/commonMain/kotlin/com/companyb/companyapp/api/ApiRoutes.kt && grep -q 'const val AUTH_REGISTER = "/auth/register"' shared/src/commonMain/kotlin/com/companyb/companyapp/api/ApiRoutes.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: OpenAPI route contract remains current
  CHECK: bash scripts/check-openapi-spec.sh
  EXPECT: EXIT 0
  EVIDENCE: To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14.3/userguide/gradle_daemon.html#sec:disabling_the_da
