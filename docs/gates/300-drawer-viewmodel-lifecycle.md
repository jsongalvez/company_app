# Gates - #300 DrawerViewModel lifecycle ownership

- [x] G1: DrawerContent no longer constructs DrawerViewModel with raw remember
  CHECK: grep -q 'remember { DrawerViewModel() }' composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/drawer/DrawerContent.kt
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: DrawerContent uses lifecycle-aware ViewModel construction
  CHECK: grep -q 'viewModel { DrawerViewModel() }' composeApp/src/commonMain/kotlin/com/companyb/companyapp/ui/drawer/DrawerContent.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: Drawer ViewModel behavior tests pass
  CHECK: ./gradlew :composeApp:desktopTest --tests '*DrawerViewModelTest*'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G4: Desktop compilation passes
  CHECK: ./gradlew :composeApp:compileKotlinDesktop
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
