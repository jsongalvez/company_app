## Question

Finish shared Auth route ownership. Replace backend login and register route literals in
`AuthRoutes` with existing `ApiRoutes.AUTH_LOGIN` and `ApiRoutes.AUTH_REGISTER`, preserving
byte-equivalent URLs, public-route behavior, and OpenAPI registration. Add focused route/source
coverage and validate shared/backend compilation and the route contract.

Evidence: fresh Map #180 C-06/C-07/C-10 audit. `ApiRoutes.kt:8-9` owns Auth paths while
`AuthRoutes.kt:48,83` still registers equivalent literals. This is a bounded P2 contract-ownership
fix; no auth behavior or new abstraction is needed.
