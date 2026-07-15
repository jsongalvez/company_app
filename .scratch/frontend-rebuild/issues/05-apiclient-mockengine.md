# 05 — MockEngine support in ApiClient

**What to build:** Add an optional `HttpClientEngine` parameter to `ApiClient` so ViewModel tests can inject a Ktor `MockEngine` and verify state transitions without a running backend. Create a `MockApiClient` helper and a first ViewModel test to establish the pattern.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `ApiClient` gains optional `engine: HttpClientEngine? = null` constructor parameter; when null, uses platform default (existing behavior)
- [ ] `MockApiClient` helper function/class that creates an `ApiClient` with `MockEngine` and a configurable response handler (status code + JSON body)
- [ ] First ViewModel test: `AuthViewModelTest.kt` in `composeApp/src/commonTest/` using `kotlin.test` + `kotlinx.coroutines.test.runTest`
  - Test: `loginSuccess` — MockEngine returns 200 + `{ "token": "fake-jwt" }`, assert `loginState` transitions Idle → Loading → Success
  - Test: `loginFailure` — MockEngine returns 401, assert Idle → Loading → Error
- [ ] `:composeApp:compileKotlinDesktop :composeApp:desktopTest` passes
