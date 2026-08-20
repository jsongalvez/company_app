# Map #180 Child: Lifecycle-own session bootstrap ViewModels

Part of #180.

## Task

Make every `SessionBootstrapViewModel` host lifecycle-owned rather than `remember`-owned.
The launch-validation instance in `App.kt` and Login route instances in mobile and desktop
hosts currently own `viewModelScope` but are created with `remember`, so host destruction can
leave bootstrap requests running and write stale user/capability state into `SessionState`.

## Scope

- Replace remembered `SessionBootstrapViewModel` construction with existing lifecycle-aware
  ViewModel construction at all three hosts.
- Preserve independent launch-validation and Login bootstrap state and existing explicit
  `cancelValidation()` behavior.
- Publish bootstrap session data through one existing-state owner seam: keep responses local until
  both requests succeed, publish capabilities before user readiness, and clear stale state on an
  authentication response.
- Add lifecycle/cancellation regression coverage at the narrowest existing test seam; production
  host disposal is verified through existing lifecycle-aware `viewModel {}` construction.
- Do not change navigation or the deferred Branch Select attendance lifecycle decision.

## Validation

- Prove old host disposal cancels in-flight bootstrap work and cannot write late global state.
- Prove current launch validation and Login retry behavior remains unchanged.
- Run affected Compose tests and Android/Desktop compilation; run full required gates at integration.
