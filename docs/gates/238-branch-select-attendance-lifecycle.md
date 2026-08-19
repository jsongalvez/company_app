# Gates — Build: own Branch Select attendance lifecycle

- [x] G1: Branch Select does not own a nested AttendanceViewModel
  CHECK: grep -q "attendanceViewModel" composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/BranchSelectViewModel.kt
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: Branch Select lifecycle regression coverage remains present
  CHECK: grep -q "clockIn_ignores_second_tap_while_in_flight" composeApp/src/commonTest/kotlin/com/companyb/companyapp/viewmodel/BranchSelectViewModelTest.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0
