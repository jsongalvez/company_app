# Gates — #176: stateful stale-failure legs

- [x] G1: `ApiCallHandler.launch` gates all three landing writes on the captured stamp
  CHECK: node -e 'const fs=require("fs"); const s=fs.readFileSync("composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/ApiCallHandler.kt","utf8"); if ((s.match(/if \(stamp\(\) == captured\)/g)||[]).length !== 3) process.exit(1)'
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: handler and VM stale-failure acceptance tests pass
  CHECK: ./gradlew :composeApp:desktopTest
  EXPECT: MATCHES BUILD SUCCESSFUL
  EVIDENCE: Reusing configuration cache.

- [x] G3: stale HTTP and exception tests pin an external in-flight stamp flip
  CHECK: grep -q 'releaseResponse.complete(Unit)' composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/ApiCallHandlerTest.kt && grep -q 'releaseFailure.complete(Unit)' composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/ApiCallHandlerTest.kt && grep -q 'in_flight_load_failure_after_404_reload_does_not_clobber_reload_success' composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/NotificationViewModelTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G4: implementation and agent contract document non-success and exception gating
  CHECK: grep -q 'no longer success-only' composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/ApiCallHandler.kt && grep -q 'no longer success-only' composeApp/AGENTS.md
  EXPECT: EXIT 0
  EVIDENCE: exit 0
