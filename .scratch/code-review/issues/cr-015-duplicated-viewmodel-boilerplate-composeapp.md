# CR-015: ~50× duplicated ViewModel try/catch/log/state boilerplate in composeApp

**Source:** Critical F — found in chunk 4 (standards hard: "~50× duplicated ViewModel boilerplate")

**What:**
Every API call in composeApp ViewModels repeats the same pattern:
```kotlin
viewModelScope.launch {
    _uiState.value = _uiState.value.copy(isLoading = true)
    try {
        logInfo(TAG, "Calling ${Endpoint}")
        val result = apiClient.someMethod(...)
        _uiState.value = _uiState.value.copy(data = result, isLoading = false)
    } catch (e: Exception) {
        logError(TAG, "Failed ${operation}", e)
        _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
    }
}
```
This pattern repeats ~50 times across ViewModels (BranchVM, SessionVM, ClientVM, etc.). Any bug in this pattern requires fixing in 50 places.

**Spec reference:** `AGENTS.md` composeApp logging convention — must use `logDebug`, `logInfo`, `logWarn`, `logError`, but the boilerplate around each call is not standardized.

**Files:** All ViewModels under `composeApp/src/commonMain/kotlin/.../viewmodel/`

**Fix:**
1. Extract a shared helper class (e.g. `ApiCallHandler<T>` or extension on ViewModel/CoroutineScope):
   ```kotlin
   suspend fun <T> callApi(
       tag: String,
       endpoint: String,
       updateState: (T) -> Unit,
       block: suspend () -> T
   ) { ... }
   ```
2. Enforce consistent error state shape across all ViewModels (add a sealed `ApiResult<T>`)
3. Migrate one ViewModel as proof-of-concept, then roll out
4. Update `AGENTS.md` composeApp logging convention to document the helper

**Priority:** medium
**Story alignment:** cross-cutting (all composeApp ViewModels)
