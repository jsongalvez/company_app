## Question

Build: finish shared route ownership for session final-price requests.

Replace the remaining Compose production literal for
`/api/sessions/{sessionId}/final-price` with `ApiRoutes.sessionFinalPrice` and add
route-builder coverage. Preserve byte-equivalent URL output and existing request
behavior.

Acceptance:

- No production Compose literal remains for this route.
- Shared route test asserts exact final-price path.
- Shared and Compose compilation plus focused ViewModel tests pass.
