# Gates — #219: shared mobile host and session list

- [x] G1: Android and iOS mobile actuals delegate to common implementations
  CHECK: test -f composeApp/src/commonMain/kotlin/com/companyb/companyapp/navigation/MobileAppNavHost.kt && test -f composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/screen/MobileSessionList.kt && grep -q 'MobileAppNavHost(' composeApp/src/androidMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.android.kt && grep -q 'MobileAppNavHost(' composeApp/src/iosMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.ios.kt && grep -q 'MobileSessionList(' composeApp/src/androidMain/kotlin/com/companyb/companyapp/ui/screen/SessionList.android.kt && grep -q 'MobileSessionList(' composeApp/src/iosMain/kotlin/com/companyb/companyapp/ui/screen/SessionList.ios.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Mobile implementation bodies are not duplicated
  CHECK: test "$(wc -l < composeApp/src/androidMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.android.kt)" -le 20 && test "$(wc -l < composeApp/src/iosMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.ios.kt)" -le 20 && test "$(wc -l < composeApp/src/androidMain/kotlin/com/companyb/companyapp/ui/screen/SessionList.android.kt)" -le 20 && test "$(wc -l < composeApp/src/iosMain/kotlin/com/companyb/companyapp/ui/screen/SessionList.ios.kt)" -le 20
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Mobile extraction has no whitespace errors
  CHECK: git diff --check
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: Desktop implementation remains separate
  CHECK: test -f composeApp/src/desktopMain/kotlin/com/companyb/companyapp/navigation/AppNavHost.desktop.kt && test -f composeApp/src/desktopMain/kotlin/com/companyb/companyapp/ui/screen/SessionList.desktop.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0
