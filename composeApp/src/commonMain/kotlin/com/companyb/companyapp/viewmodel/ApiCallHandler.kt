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

/**
 * Params-object for [ApiCallHandler.launch]'s full parameter list (#462 LPL burn: 11 params;
 * data classes are LPL-free — the MutationRequest/FeedWindowRequest precedent). Plain
 * `launch` call sites keep the 5-param overload ([ApiCallHandler.launch] state/operation/
 * endpoint/block/transform) untouched; a site that uses any hook below wraps its full list
 * in this class instead.
 *
 * Tail-field contracts (carried verbatim from the pre-#462 parameter list):
 *
 * - [onNonSuccess] — status-branching hook for non-success responses. Returns true when the
 *   caller fully handled the response (state assigned or deliberately left untouched) — the
 *   generic Error assignment is then skipped. Default false keeps the historical behavior.
 *   #113: D4's 403 silent-exit + 409 reload paths branch on status here.
 * - [onError] — failure hook invoked on the EXCEPTION path (transport/timeout/deserialization
 *   failures caught in the handler) — [onNonSuccess] only sees HTTP-status responses, so a
 *   caller counting failures (e.g. #147's stale/error poll thresholds) must hook here to see
 *   thrown failures too. Additive; default no-op keeps existing callers.
 * - [scope] — Job-scoping override: the launch adopts the caller's scope (structured
 *   concurrency) instead of the handler's construction scope. #113's debounced search launches
 *   through a per-query Job so a newer keystroke cancels the in-flight request (D2 current-query
 *   guard): the stale response then dies at the cancellation instead of committing. `null`
 *   (the default) resolves to the handler's construction scope.
 * - [stamp]/[fallback] — the #165 stale-substitution guard, the resurrect-invariant (#141) at
 *   handler level. The identical `if (stamp != actionStamp) { reissue(); freshest() ?:
 *   emptyList() } else body` substitution block was hand-rolled at
 *   NotificationVM.loadUnreadNotifications + ReliefInviteVM.loadReceived; a load that lands
 *   after an action moved the state it was launched against must not commit its pre-action
 *   snapshot. [stamp] is a value-source read twice — once synchronously at launch invocation
 *   (captured; exact at launch, not at coroutine start), once at landing. A SUCCESS landing
 *   commits transform(response) only when the two reads agree; a mismatched success-compatible
 *   landing commits [fallback]() instead — the caller's substitution (a freshest-value read) +
 *   re-issue (a new launch carrying the post-action stamp). The stamp is rechecked AGAIN
 *   after the suspend [transform] returns (#490 — a generation bump mid-deserialization must
 *   not commit the stale body); callers must also guard side effects inside [transform]
 *   itself with the same comparison, since the handler cannot retract those. The FAILURE legs (#176 — the guard
 *   is no longer success-only): a non-success or exception landing may run its hooks but writes
 *   UiState.Error only when the reads agree — a superseded failure writes nothing onto the
 *   moved-on surface. A stale body is never deserialized — its content is irrelevant to the
 *   invariant, and skipping the parse means a malformed stale body can no longer surface an
 *   Error for a response the caller would discard. (A body that goes stale MID-transform is
 *   parsed, then dropped by the post-transform recheck — #490.) Defaults are a no-op: a constant stamp
 *   always agrees, keeping every existing caller behavior-identical (the fallback default is
 *   unreachable then — a guard enabled without one fails loudly, not silently).
 */
data class LaunchRequest<T>(
    val state: MutableStateFlow<UiState<T>>,
    val operation: String,
    val endpoint: String,
    val block: suspend () -> HttpResponse,
    val transform: suspend (HttpResponse) -> T,
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    val onNonSuccess: suspend (HttpResponse) -> Boolean = { false },
    val onError: (Throwable) -> Unit = {},
    val scope: CoroutineScope? = null,
    val stamp: () -> Long = { 0L },
    val fallback: () -> T = { error("stale-guard fallback invoked without a fallback param") },
)

/**
 * Tail-bundle for [ApiCallHandler.launchUnit] (#462 LPL burn: 8 params; data classes are
 * LPL-free). The plain launchUnit shape (state/operation/endpoint/block) is untouched —
 * only sites that pass a hook below wrap those hooks in this class. The #165 stamp/fallback
 * guard belongs to [LaunchRequest] alone: launchUnit callers never gated a stale landing.
 */
data class LaunchHooks(
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    val onNonSuccess: suspend (HttpResponse) -> Boolean = { false },
    val onError: (Throwable) -> Unit = {},
    // Job-scoping override (the #113 debounced-search shape); null resolves to the handler's
    // construction scope.
    val scope: CoroutineScope? = null,
)

/**
 * Tail-bundle for [ApiCallHandler.launchStateless] (#462 LPL burn: 9 params; data classes
 * are LPL-free). The plain launchStateless shape (operation/endpoint/block/transform) is
 * untouched — only sites that pass a hook below wrap those hooks in this class.
 *
 * Ungated only: no generation guard lives here. A surface that can move on
 * (window/branch/mode/filter switch) must use [GuardedStateless] +
 * [ApiCallHandler.launchStatelessGuarded] instead — a one-time stale check around a
 * suspending decode cannot retract side effects (#528).
 */
data class StatelessHooks(
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    val onNonSuccess: suspend (HttpResponse) -> Unit = {},
    val onError: (Throwable) -> Unit = {},
    // Job-scoping override (the #113 debounced-search shape); null resolves to the handler's
    // construction scope.
    val scope: CoroutineScope? = null,
)

/**
 * Decode/commit split for generation-guarded stateless surfaces (#528).
 *
 * [decode] is the suspend work (fetch already happened in `block`; this is body parsing —
 * `body<T>()`, `bodyAsText()`, `readRawBytes()`). [commit] is the non-suspending guarded
 * state commit — list/cursor/flags writes, action-tracker terminals, `finish()` — run
 * immediately after the second [stale] read with no suspension gap, on the handler's scope
 * (the state owner's dispatcher). [onNonSuccess]/[onError] are likewise non-suspending
 * commits for the failure legs: they see only status/throwable, never a suspending body
 * read, so the single pre-commit [stale] read closes them with no gap.
 *
 * The handler skips already-stale bodies before [decode] (no wasted parse) and drops bodies
 * that go stale mid-decode before [commit]. A generic post-callback check around a combined
 * decode+commit lambda cannot do this — the side effects inside the lambda are unretractable.
 */
data class GuardedStateless<D>(
    val decode: suspend (HttpResponse) -> D,
    val commit: (D) -> Unit,
    // When true the landing is inert — the caller's captured generation vs the current field.
    val stale: () -> Boolean,
    val onNonSuccess: (HttpResponse) -> Unit = {},
    val onError: (Throwable) -> Unit = {},
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    // Job-scoping override (the #113 debounced-search shape); null resolves to the handler's
    // construction scope.
    val scope: CoroutineScope? = null,
)

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
    ): Job =
        launch(
            LaunchRequest(
                state = state,
                operation = operation,
                endpoint = endpoint,
                block = block,
                transform = transform,
            ),
        )

    @Suppress("TooGenericExceptionCaught")
    fun <T> launch(request: LaunchRequest<T>): Job {
        logInfo(tag, request.entryMessage ?: "${request.operation} called")
        val captured = request.stamp()
        return (request.scope ?: scope).launch {
            request.state.value = UiState.Loading
            try {
                logInfo(tag, request.endpoint)
                val response = request.block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "${request.operation} success")
                    handleSuccess(request, response, captured)
                } else {
                    logWarn(tag, "${request.operation} failed: status=${response.status.value}")
                    if (!request.onNonSuccess(response)) {
                        // #176 stateful stale-failure leg: a superseded failure writes nothing
                        // onto the moved-on surface (the guard is no longer success-only).
                        if (request.stamp() == captured) {
                            request.state.value =
                                UiState.Error("${request.operation} failed: ${response.status.value}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Re-throw: a cancelled launch (e.g. #113's debounce job cancelled by a newer
                // keystroke) must not surface as Error — cancellation isn't a request failure.
                throw e
            } catch (e: Exception) {
                logError(tag, "${request.operation} exception on ${request.endpoint}", e)
                request.onError(e)
                // #176 stateful stale-failure leg: the hook keeps running, but a superseded
                // failure's Error must not clobber the moved-on surface's newer write.
                if (request.stamp() == captured) {
                    request.state.value = UiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // #499 — success-leg extracted to keep launch under CognitiveComplexMethod threshold.
    private suspend fun <T> handleSuccess(
        request: LaunchRequest<T>,
        response: HttpResponse,
        captured: Long,
    ) {
        if (request.stamp() == captured) {
            val parsed = request.transform(response)
            // #490 — recheck after the suspend transform: a bump mid-deserialization
            // still drops the stale body (its content is irrelevant to the invariant).
            if (request.stamp() == captured) {
                request.state.value = UiState.Success(parsed)
            } else {
                request.state.value = UiState.Success(request.fallback())
            }
        } else {
            request.state.value = UiState.Success(request.fallback())
        }
    }

    fun launchUnit(
        state: MutableStateFlow<UiState<Unit>>,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        hooks: LaunchHooks = LaunchHooks(),
    ): Job =
        launch(
            LaunchRequest(
                state = state,
                operation = operation,
                endpoint = endpoint,
                block = block,
                transform = { Unit },
                entryMessage = hooks.entryMessage,
                onNonSuccess = hooks.onNonSuccess,
                onError = hooks.onError,
                scope = hooks.scope,
            ),
        )

    // State-less launch (the #162 P5 handler state-less launch graduate, #168): the same
    // logging / cancellation / onNonSuccess / onError discipline as [launch], but NO state
    // writes — no Loading, no Success, no Error. For callers whose observable effect is a
    // side effect inside [transform] (the action-tracker terminal paths of
    // UserViewModel.runMutation / AuditLogViewModel acknowledge, the keyed export writes of
    // FinanceReportsViewModel.exportMode, the branch-name write of
    // ReliefDayViewModel.resolveBranchName), the state param was
    // a throwaway flow no consumer reads — this variant makes the adapter unnecessary.
    // [onNonSuccess] has no Boolean "handled" contract: there is no generic Error assignment
    // to skip, so the caller's hook runs and that is all.
    //
    // Ungated by design (#528): a surface that can move on while a request is in flight must
    // use [launchStatelessGuarded] — a one-time stale check around a suspending decode cannot
    // retract the decode's side effects.
    @Suppress("TooGenericExceptionCaught")
    fun launchStateless(
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        transform: suspend (HttpResponse) -> Unit,
        hooks: StatelessHooks = StatelessHooks(),
    ): Job {
        logInfo(tag, hooks.entryMessage ?: "$operation called")
        return (hooks.scope ?: scope).launch {
            try {
                logInfo(tag, endpoint)
                val response = block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "$operation success")
                    transform(response)
                } else {
                    logWarn(tag, "$operation failed: status=${response.status.value}")
                    hooks.onNonSuccess(response)
                }
            } catch (e: CancellationException) {
                // Re-throw: a cancelled launch (e.g. #113's debounce job cancelled by a newer
                // keystroke) must not surface as an error — cancellation isn't a request failure.
                throw e
            } catch (e: Exception) {
                logError(tag, "$operation exception on $endpoint", e)
                hooks.onError(e)
            }
        }
    }

    // Guarded stateless launch (#528): the decode/commit split. [GuardedStateless.decode]
    // suspends (body parsing); [GuardedStateless.commit] is non-suspending and runs
    // immediately after the second stale read with no suspension gap, on this scope. The
    // first stale read skips already-stale bodies before the wasted parse; the failure legs
    // are non-suspending commits closed by a single pre-commit stale read.
    @Suppress("TooGenericExceptionCaught")
    fun <D> launchStatelessGuarded(
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        guarded: GuardedStateless<D>,
    ): Job {
        logInfo(tag, guarded.entryMessage ?: "$operation called")
        return (guarded.scope ?: scope).launch {
            try {
                logInfo(tag, endpoint)
                val response = block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "$operation success")
                    if (guarded.stale()) return@launch
                    val decoded = guarded.decode(response)
                    if (guarded.stale()) return@launch
                    guarded.commit(decoded)
                } else {
                    logWarn(tag, "$operation failed: status=${response.status.value}")
                    if (guarded.stale()) return@launch
                    guarded.onNonSuccess(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(tag, "$operation exception on $endpoint", e)
                if (guarded.stale()) return@launch
                guarded.onError(e)
            }
        }
    }
}
