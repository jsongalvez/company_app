# Gates - 270: AuthViewModel lifecycle ownership

- [x] G1: Mobile and Desktop login hosts use lifecycle-owned AuthViewModel construction
  CHECK: test -n "$(grep -F 'authViewModel = viewModel { AuthViewModel(apiClient) }' composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/MobileAppNavHost.kt)" && test -n "$(grep -F 'authViewModel = viewModel { AuthViewModel(apiClient) }' composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt)" && printf 'mobile and desktop lifecycle-owned AuthViewModel hosts'
  EXPECT: mobile and desktop lifecycle-owned AuthViewModel hosts
  EVIDENCE: mobile and desktop lifecycle-owned AuthViewModel hosts

- [x] G2: Compose source has no raw AuthViewModel remember construction
  CHECK: ! grep -R -E 'remember \{ AuthViewModel' composeApp/src
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: AuthViewModel tests pass
  CHECK: ./gradlew :composeApp:desktopTest --tests com.companyb.companyapp.viewmodel.AuthViewModelTest
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
