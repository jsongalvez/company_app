# Compose Multiplatform App

## Targets

- Android (`androidMain/`)
- Desktop JVM (`desktopMain/`)
- iOS (`iosMain/`)

## Validation

Targeted and agent-invoked (map #329): a narrow desktop UI change compiles
`:composeApp:compileKotlinDesktop` only — never every platform target by default. VM logic
adds `:composeApp:desktopTest --tests '<Fqcn>'`. Broader target compiles are risk-based:
common/shared contract changes may justify them. `bash tools/quality/validate.sh` auto-selects.
Full multi-platform Detekt/test coverage is asynchronous CI work.

## Design language

The project uses the Linear design system defined in `composeApp/DESIGN.md`. All new composables reference its token conventions: dark canvas (`#010102`), four-step surface ladder, lavender-blue accent (`#5e6ad2`), hairline borders instead of shadows.

## Comment hygiene (#460, scoped — never a blanket ban)

Delete redundant what-comments; keep `#<ticket>` decision traceability and KDoc on seams/contracts. Encode cheap constraints in types/tests/lint instead of prose. `ForbiddenComment` (TODO/FIXME/STOPSHIP + anti-slop TEMP/PLACEHOLDER/HACK/XXX/NOT-IMPLEMENTED) stays on. No comment-count gate, no `no-comments` CI rule — per-diff review only.

## Logging convention

All composeApp code uses `expect/actual Log` functions from `com.companyb.companyapp.util`:

- `logDebug(tag, message)`, `logInfo(tag, message)`, `logWarn(tag, message)`, `logError(tag, message, throwable?)`
- Desktop → SLF4J/logback, Android → android.util.Log, iOS → println with timestamp prefix.
- **Tag naming**: `"[Feature]VM"` for ViewModels (e.g. `"BranchVM"`, `"SessionVM"`), screen name for composables (`"LoginScreen"`, `"HomeScreen"`), `"TokenStore"`, `"ApiClient"`.
- **Where to log**: method entry, API call start (with endpoint path), success/failure, and catch blocks.
- `logWarn`: handled business errors (HTTP 4xx responses, `UiState.Error` branches in screens — not exceptions).
- `logError` must be used in every `catch` block with the exception as the third arg.
- New ViewModels/screens must follow this convention.

## ApiCallHandler

ViewModels must use `ApiCallHandler` (`com.companyb.companyapp.async`) for all API calls instead of writing inline `try/catch/log/state` boilerplate:

```kotlin
class ExampleViewModel(private val apiClient: ApiClient) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ExampleVM")

    fun loadData() {
        handler.launch(
            state = _data,
            operation = "loadData",
            endpoint = "GET /api/example",
            block = { apiClient.httpClient.get("/api/example") },
            transform = { it.body() },
        )
    }

    fun performAction() {
        handler.launchUnit(
            state = _actionResult,
            operation = "performAction",
            endpoint = "POST /api/example/action",
            block = { apiClient.httpClient.post("/api/example/action") },
        )
    }
}
```

- `launch`: for calls that deserialize a response body. Use `transform = { it.body() }` for standard deserialization.
- `launchUnit`: for calls that only need success/failure status (no response body).
- `entryMessage`: optional parameter to customize the entry log (defaults to `"$operation called"`).
- The handler automatically manages `UiState.Loading`, `UiState.Success`, `UiState.Error`, and all logging (`logInfo` for lifecycle, `logError` for exceptions). The stateful variants (`launch`/`launchUnit`) write all three states.
- **Freshness policies (#611)** —ordinary one-shot `launch` has no generation; loads whose surface can move on pick one:
  - `launchLatest` + `LatestLoad` (session roster/members, remittance detail/pickers): latest-request-wins — a superseded landing commits nothing and reissues nothing. Generation is owned by a `LoadGeneration` (advanced per load inside the handler).
  - `launchReconciling` + `ReconcilingLoad` (notification/invite/attendance rosters): post-mutation retain-and-reload — a stale pre-action snapshot commits nothing and reissues instead; the post-action list stays retained by the action's synchronous KeepLast write. Actions own an `ActionStamp` (bumped alongside the mutation).
  - `launchStateless` — the state-less variant (#168): no `state` param, no `UiState` writes at all (no Loading/Success/Error). Only for callers whose observable effect is a side effect in `transform`/`onNonSuccess`/`onError` (keyed-mirror commits, action-tracker terminal paths, list writes done manually in the hooks). Never pass a throwaway `MutableStateFlow` to the stateful variants — use `launchStateless` instead.
  - `launchStatelessGuarded` + `GuardedStateless` — the state-less latest-wins variant (#528): `decode` suspends (body parse, pure) and `commit` is non-suspending (runs only when current); stale landings are inert. Failure commits stay non-suspending and status-only — suspending failure-body reads live on unguarded `launch` paths instead.
  - Full decode/commit/stale contracts live as KDoc on the bundle types in `async/ApiCallHandler.kt` — read them at the call site instead of duplicating the protocol here.
- ViewModels that use `ApiCallHandler` exclusively do not need to import `logInfo`, `logError`, or `launch` from kotlinx.coroutines.

## State ownership vs file splits (#535)

- Prefer private members or a genuine state-owning collaborator over ViewModel
  extension files that need mutable internals (`internal` state, exposed API
  clients) merely to satisfy a function count. A cohesive owner with many
  functions is not a violation — `TooManyFunctions` is retired as a gate.
- `LongParameterList` (6 params) is a design signal, not an obligation to
  manufacture DTO wrappers: keep coherent state-owner and declarative-UI
  signatures whole; bundle parameters only where the bundle is a real
  ownership decision, with a `#<ticket>` rationale where suppressed.
