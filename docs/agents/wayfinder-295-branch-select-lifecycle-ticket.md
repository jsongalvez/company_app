# Build: own Branch Select attendance lifecycle

Part of Map #180.

## Question

Remove nested `AttendanceViewModel` ownership from `BranchSelectViewModel`. Run Branch Select clock-in through its existing parent `viewModelScope` and preserve the standalone Drawer clock-out flow, capability refresh ordering, double-tap guard, and route re-entry behavior. Add lifecycle and repeated-attempt coverage.
