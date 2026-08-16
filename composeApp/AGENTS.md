# Compose Multiplatform App

## Targets

- Android (`androidMain/`)
- Desktop JVM (`desktopMain/`)
- iOS (`iosMain/`)

## Design language

The project uses the Linear design system defined in `composeApp/DESIGN.md`. All new composables reference its token conventions: dark canvas (`#010102`), four-step surface ladder, lavender-blue accent (`#5e6ad2`), hairline borders instead of shadows.

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

ViewModels must use `ApiCallHandler` (`com.companyb.companyapp.viewmodel`) for all API calls instead of writing inline `try/catch/log/state` boilerplate:

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
- **`launchStateless`** — the state-less variant (#168): no `state` param, no `UiState` writes at all (no Loading/Success/Error). Only for callers whose observable effect is a side effect in `transform`/`onNonSuccess`/`onError` (keyed-mirror commits, action-tracker terminal paths, list writes done manually in the hooks). Never pass a throwaway `MutableStateFlow` to the stateful variants — use `launchStateless` instead.
- **`stale: () -> Boolean`** (`launchStateless`, #173) — the skip-gate: when it reads true at a landing, all three hooks (transform/onNonSuccess/onError) are skipped — a superseded landing is inert. This is where the per-site generation guards live (a caller whose surface can move on — window/branch/mode/filter switch — passes `stale = { generation != itsGeneration }` with the launch-time capture; see FinanceReportsViewModel/AuditLogViewModel). Default `{ false }` = the gate never fires. The stateful `launch` owns its stale class via the #165 `stamp`/`fallback` SUBSTITUTION shape instead — do not port it there, and give this gate no substitute-fallback: stateless surfaces only skip (a stale body is never deserialized).
- ViewModels that use `ApiCallHandler` exclusively do not need to import `logInfo`, `logError`, or `launch` from kotlinx.coroutines.
