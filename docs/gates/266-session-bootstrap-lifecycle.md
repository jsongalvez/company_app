# Gates - #266: lifecycle-own session bootstrap ViewModels

- [x] G1: No production host constructs `SessionBootstrapViewModel` with `remember`
  CHECK: if grep -R -n 'remember.*SessionBootstrapViewModel' composeApp/src/commonMain/kotlin/com/companyb/companyapp/App.kt composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/MobileAppNavHost.kt composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt; then exit 1; else exit 0; fi
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: All three production bootstrap host sites use lifecycle-aware construction
  CHECK: test "$(grep -R -l 'SessionBootstrapViewModel' composeApp/src/commonMain/kotlin/com/companyb/companyapp/App.kt composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/MobileAppNavHost.kt composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt | wc -l)" -eq 3 && ! grep -R -n -E 'remember[[:space:]]*\{[[:space:]]*SessionBootstrapViewModel' composeApp/src/commonMain/kotlin/com/companyb/companyapp/App.kt composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/MobileAppNavHost.kt composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Android and Desktop Compose sources compile with existing common ViewModel tests
  CHECK: ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop :composeApp:test >/dev/null && echo BUILD SUCCESSFUL
  EXPECT: MATCHES BUILD SUCCESSFUL
  EVIDENCE: BUILD SUCCESSFUL
