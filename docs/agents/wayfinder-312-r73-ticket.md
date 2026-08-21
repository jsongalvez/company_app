Part of #180

## Question

Make Branch Select `ReliefInviteViewModel` instances lifecycle-owned at Android and Desktop route hosts.

## Scope

- Replace composition-scoped construction with existing `viewModel {}` construction at both Branch Select hosts.
- Preserve notification-route instances and existing ViewModel behavior.
- Validate Compose Android/Desktop compilation and lifecycle-focused tests.
