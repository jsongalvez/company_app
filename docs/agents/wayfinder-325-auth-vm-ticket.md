## Question

Build: lifecycle-own AuthViewModel instances in mobile and Desktop navigation hosts.

Replace raw `remember { AuthViewModel(apiClient) }` construction with the existing
lifecycle-aware `viewModel { }` pattern. Preserve login/register behavior and add or
update focused cancellation/lifecycle coverage where the current test surface permits.

Acceptance:

- Mobile and Desktop hosts use lifecycle-owned AuthViewModel construction.
- Login and registration flows retain existing behavior.
- Focused Compose tests pass.
- Android and Desktop compilation pass; iOS compilation is attempted when the required
  Kotlin Native artifact is available.
