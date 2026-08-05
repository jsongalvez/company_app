package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ApiCallHandler(
    private val scope: CoroutineScope,
    private val tag: String,
) {
    fun <T> launch(
        state: MutableStateFlow<UiState<T>>,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        transform: suspend (HttpResponse) -> T,
        entryMessage: String = "$operation called",
        // Status-branching hook for non-success responses. Returns true when the caller fully
        // handled the response (state assigned or deliberately left untouched) — the generic
        // Error assignment is then skipped. Default false keeps the historical behavior.
        // #113: D4's 403 silent-exit + 409 reload paths branch on status here.
        onNonSuccess: suspend (HttpResponse) -> Boolean = { false },
        // Job-scoping override — the launch adopts the caller's scope (structured concurrency)
        // instead of the handler's construction scope. #113's debounced search launches through a
        // per-query Job so a newer keystroke cancels the in-flight request (D2 current-query
        // guard): the stale response then dies at the cancellation instead of committing.
        scope: CoroutineScope = this.scope,
    ): Job {
        logInfo(tag, entryMessage)
        return scope.launch {
            state.value = UiState.Loading
            try {
                logInfo(tag, endpoint)
                val response = block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "$operation success")
                    state.value = UiState.Success(transform(response))
                } else {
                    logWarn(tag, "$operation failed: status=${response.status.value}")
                    if (!onNonSuccess(response)) {
                        state.value = UiState.Error("$operation failed: ${response.status.value}")
                    }
                }
            } catch (e: CancellationException) {
                // Re-throw: a cancelled launch (e.g. #113's debounce job cancelled by a newer
                // keystroke) must not surface as Error — cancellation isn't a request failure.
                throw e
            } catch (e: Exception) {
                logError(tag, "$operation exception on $endpoint", e)
                state.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun launchUnit(
        state: MutableStateFlow<UiState<Unit>>,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        entryMessage: String = "$operation called",
        onNonSuccess: suspend (HttpResponse) -> Boolean = { false },
        scope: CoroutineScope = this.scope,
    ): Job = launch(state, operation, endpoint, block, { Unit }, entryMessage, onNonSuccess, scope)
}
