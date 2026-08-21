## Question

Make `DrawerViewModel` lifecycle-owned. Replace raw `remember` construction in `DrawerContent`
with the existing lifecycle-aware `viewModel {}` API. Preserve capability collection, drawer
state, logout/clock-out behavior, and existing UI behavior. Add or update focused lifecycle/source
coverage and validate common tests plus Android/Desktop compilation.

Evidence: fresh Map #180 C-01..C-04 audit. `DrawerContent.kt:76-82` constructs a
`DrawerViewModel` with `remember`; `DrawerViewModel.kt:30,60-84` owns `viewModelScope`. Route
removal can leave its collector and in-flight work alive without `onCleared`.
