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
        // Failure hook invoked on the EXCEPTION path (transport/timeout/deserialization
        // failures caught below) — onNonSuccess only sees HTTP-status responses, so a
        // caller counting failures (e.g. #147's stale/error poll thresholds) must hook
        // here to see thrown failures too. Additive; default no-op keeps existing callers.
        onError: (Throwable) -> Unit = {},
        // Job-scoping override — the launch adopts the caller's scope (structured concurrency)
        // instead of the handler's construction scope. #113's debounced search launches through a
        // per-query Job so a newer keystroke cancels the in-flight request (D2 current-query
        // guard): the stale response then dies at the cancellation instead of committing.
        scope: CoroutineScope = this.scope,
        // #165 stale-substitution guard — the resurrect-invariant (#141) at handler level. The
        // identical `if (stamp != actionStamp) { reissue(); freshest() ?: emptyList() } else body`
        // substitution block was hand-rolled at NotificationVM.loadUnreadNotifications +
        // ReliefInviteVM.loadReceived; a load that lands after an action moved the state it was
        // launched against must not commit its pre-action snapshot. checkpoint() captures a
        // stamp synchronously at launch invocation (the caller's `val loadStamp = actionStamp`
        // timing — exact at launch, not at coroutine start); on a success response the handler
        // commits transform(response) only when isCurrent(stamp), else fallback() — the
        // caller's substitution (a freshest-value read) + re-issue (a new launch carrying the
        // post-action stamp). A stale body is never deserialized — its content is irrelevant to
        // the invariant, and skipping the parse means a malformed stale body can no longer
        // surface an Error for a response the caller would discard. Defaults are a no-op:
        // isCurrent always true keeps every existing caller behavior-identical (the fallback
        // default is unreachable then — a guard enabled without one fails loudly, not silently).
        checkpoint: () -> Long = { 0L },
        isCurrent: (Long) -> Boolean = { true },
        fallback: () -> T = { error("stale-guard fallback invoked without an isCurrent guard") },
    ): Job {
        logInfo(tag, entryMessage)
        val stamp = checkpoint()
        return scope.launch {
            state.value = UiState.Loading
            try {
                logInfo(tag, endpoint)
                val response = block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "$operation success")
                    if (isCurrent(stamp)) {
                        state.value = UiState.Success(transform(response))
                    } else {
                        state.value = UiState.Success(fallback())
                    }
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
                onError(e)
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
        onError: (Throwable) -> Unit = {},
        scope: CoroutineScope = this.scope,
    ): Job =
        launch(
            state = state,
            operation = operation,
            endpoint = endpoint,
            block = block,
            transform = { Unit },
            entryMessage = entryMessage,
            onNonSuccess = onNonSuccess,
            onError = onError,
            scope = scope,
        )
}
