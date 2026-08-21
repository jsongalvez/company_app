# Gates — 294: shared auth routes

- [x] G1: AuthViewModel uses shared login and register route constants
  CHECK: grep -nE 'post\("/auth/(login|register)"\)|endpoint = "POST /auth/(login|register)"' composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/AuthViewModel.kt
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: AuthViewModel tests pass
  CHECK: ./gradlew :composeApp:desktopTest --tests '*AuthViewModelTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
