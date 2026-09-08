package com.companyb.companyapp.async

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
 *
 * Freshness guards live outside this class (#611): latest-request-wins loads use
 * [LatestLoad] + [ApiCallHandler.launchLatest], post-mutation retain-and-reload loads use
 * [ReconcilingLoad] + [ApiCallHandler.launchReconciling]. This request stays the plain
 * one-shot shape — every landing is current, no generation is read.
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
)

/**
 * Tail-bundle for [ApiCallHandler.launchUnit] (#462 LPL burn: 8 params; data classes are
 * LPL-free). The plain launchUnit shape (state/operation/endpoint/block) is untouched —
 * only sites that pass a hook below wrap those hooks in this class.
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

/**
 * Latest-request generation owner (#611): advanced once per LOAD, inside
 * [ApiCallHandler.launchLatest] at launch invocation (synchronous — exact at launch, not at
 * coroutine start). A landing whose captured generation disagrees was superseded by a newer
 * load — it commits nothing and reissues nothing (the superseding load is already in flight).
 *
 * Distinct from [ActionStamp] (advanced per ACTION, retain-and-reload policy): do not
 * interchange them — a latest load carrying an action stamp (or vice versa) silently picks
 * the wrong stale policy. The compiler enforces the split: [LatestLoad] takes this type,
 * [ReconcilingLoad] takes [ActionStamp].
 */
class LoadGeneration {
    private var current = 0L

    fun next(): Long = ++current

    fun isStale(captured: Long): Boolean = captured != current
}

/**
 * Post-mutation reconciliation stamp owner (#611): advanced by ACTIONS ([bump],
 * synchronously in the action's transform alongside the KeepLast in-place mutation),
 * captured per load ([capture], synchronously at launch invocation inside
 * [ApiCallHandler.launchReconciling]). A landing with a mismatched capture predates the
 * action — its pre-action snapshot must not commit (the #141 resurrect class);
 * [ApiCallHandler.launchReconciling] reissues instead so server truth converges. The
 * post-action list itself is retained by the action's synchronous KeepLast write, never by
 * the stale landing.
 *
 * Distinct from [LoadGeneration] (advanced per load, discard policy): do not interchange them.
 */
class ActionStamp {
    private var current = 0L

    fun bump() {
        current++
    }

    fun capture(): Long = current

    fun isStale(captured: Long): Boolean = captured != current
}

/**
 * Latest-request-wins stateful load (#611): the session-roster / remittance-detail shape.
 * A stale landing commits nothing — no fallback data is invented, no control-flow exception
 * is thrown to hold Loading, no reissue fires. A double-initial overlap whose stale landing
 * arrives first simply holds Loading until the superseding load lands.
 *
 * - [decode] suspends (body parse, pure — no state writes: the handler cannot retract them).
 *   Already-stale bodies skip the parse, so a malformed stale body can never surface; a bump
 *   mid-decode drops the parsed body after it.
 * - [onCommit] runs atomically with the Success write when current (non-suspending — no
 *   suspension gap for a bump to slip through): marker writes that used to hide inside the
 *   suspend transform (remittance picker range) belong here.
 * - Failure legs are non-suspending commits on purpose: a suspending failure-body read would
 *   need its own post-decode recheck, which this bundle cannot retract — suspending failure
 *   detail (bodyAsText error extraction) lives on unguarded [LaunchRequest] paths instead
 *   (the Delegate assign/revoke precedent). [onNonSuccess] returning true skips the generic
 *   Error; stale failures still run their hooks (#176 preserved) but never write Error.
 */
data class LatestLoad<T>(
    val state: MutableStateFlow<UiState<T>>,
    val operation: String,
    val endpoint: String,
    val block: suspend () -> HttpResponse,
    val decode: suspend (HttpResponse) -> T,
    val guard: LoadGeneration,
    val onCommit: (T) -> Unit = {},
    val onNonSuccess: (HttpResponse) -> Boolean = { false },
    val onError: (Throwable) -> Unit = {},
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    // Job-scoping override (the #113 debounced-search shape); null resolves to the handler's
    // construction scope.
    val scope: CoroutineScope? = null,
)

/**
 * Post-mutation retain-and-reload stateful load (#611): the notification / invite /
 * attendance-roster shape. A stale pre-action snapshot commits nothing (no resurrect);
 * instead [reissue] fires — a fresh load carrying the post-action stamp — so server truth
 * converges. The post-action list is retained by the action's synchronous KeepLast
 * mutation, never by recommitting the freshest mirror here (it is already the state).
 *
 * Decode/commit/failure contracts match [LatestLoad] (pure suspending decode, non-suspending
 * guarded commits, #176 stale-failure hook semantics preserved). The two bundles differ
 * only in stale-success handling — discard vs reissue — and the compiler keeps them apart
 * via [LoadGeneration] vs [ActionStamp].
 */
data class ReconcilingLoad<T>(
    val state: MutableStateFlow<UiState<T>>,
    val operation: String,
    val endpoint: String,
    val block: suspend () -> HttpResponse,
    val decode: suspend (HttpResponse) -> T,
    val stamp: ActionStamp,
    val reissue: () -> Unit,
    val onCommit: (T) -> Unit = {},
    val onNonSuccess: (HttpResponse) -> Boolean = { false },
    val onError: (Throwable) -> Unit = {},
    // Optional entry-log override; null resolves to "$operation called".
    val entryMessage: String? = null,
    // Job-scoping override (the #113 debounced-search shape); null resolves to the handler's
    // construction scope.
    val scope: CoroutineScope? = null,
)

/**
 * Concise contract (#611 — the full per-policy details live on the bundle types above):
 *
 * - One-shot loads: [launch] / [launchUnit] (stateful) and [launchStateless] (side-effect
 *   only). Every landing is current; no generation is read. Suspending failure-body reads
 *   (bodyAsText error extraction) live here — guarded commits below stay non-suspending.
 * - Latest-request-wins stateful loads: [launchLatest] + [LatestLoad] (session roster,
 *   remittance detail/pickers). Stale landings commit nothing and reissue nothing.
 * - Post-mutation retain-and-reload stateful loads: [launchReconciling] + [ReconcilingLoad]
 *   (notification / invite / attendance rosters). Stale snapshots commit nothing and reissue.
 * - Stateless latest-wins surfaces: [launchStatelessGuarded] + [GuardedStateless] (feed,
 *   browse, keyed mirrors, search). Stale landings are inert — decode/commit split.
 *
 * All five paths share the single [Outcome]-producing block lifecycle below
 * (endpoint/success/failure logging, CancellationException rethrow); only the landing
 * policy differs per path.
 */
class ApiCallHandler(
    private val scope: CoroutineScope,
    private val tag: String,
) {
    private sealed interface Outcome {
        data class Ok(
            val response: HttpResponse,
        ) : Outcome

        data class NonSuccess(
            val response: HttpResponse,
        ) : Outcome

        data class Threw(
            val e: Exception,
        ) : Outcome
    }

    // Single block lifecycle (#611): endpoint/success/failure logging plus the #113
    // cancellation discipline (a cancelled launch rethrows — cancellation isn't a request
    // failure — and never surfaces as an error hook). Callers map the outcome onto their
    // landing policy; block exceptions arrive as [Outcome.Threw] (already logged), while
    // decode/commit exceptions stay the caller's try/catch below.
    @Suppress("TooGenericExceptionCaught") // #637 generic catch, CancellationException rethrows (#585 precedent)
    private suspend fun execute(
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
    ): Outcome =
        try {
            logInfo(tag, endpoint)
            val response = block()
            if (response.status.isSuccess()) {
                logInfo(tag, "$operation success")
                Outcome.Ok(response)
            } else {
                logWarn(tag, "$operation failed: status=${response.status.value}")
                Outcome.NonSuccess(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(tag, "$operation exception on $endpoint", e)
            Outcome.Threw(e)
        }

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

    @Suppress("TooGenericExceptionCaught") // #585 generic catch, CancellationException rethrows (moved #554)
    fun <T> launch(request: LaunchRequest<T>): Job {
        logInfo(tag, request.entryMessage ?: "${request.operation} called")
        return (request.scope ?: scope).launch {
            request.state.value = UiState.Loading
            try {
                when (val outcome = execute(request.operation, request.endpoint, request.block)) {
                    is Outcome.Ok -> {
                        request.state.value = UiState.Success(request.transform(outcome.response))
                    }

                    is Outcome.NonSuccess -> {
                        if (!request.onNonSuccess(outcome.response)) {
                            request.state.value =
                                UiState.Error("${request.operation} failed: ${outcome.response.status.value}")
                        }
                    }

                    is Outcome.Threw -> {
                        request.onError(outcome.e)
                        request.state.value = UiState.Error(outcome.e.message ?: "Unknown error")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(tag, "${request.operation} exception on ${request.endpoint}", e)
                request.onError(e)
                request.state.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Latest-request-wins stateful load (#611 — [LatestLoad]): every launch advances
     * [LatestLoad.guard] synchronously at invocation; a landing whose capture disagrees is
     * superseded and commits nothing (see [commitLatestSuccess] for the success path;
     * failure legs keep the #176 hook semantics — hooks run, Error writes are gated).
     */
    @Suppress("TooGenericExceptionCaught") // #585 generic catch, CancellationException rethrows (moved #554)
    fun <T> launchLatest(load: LatestLoad<T>): Job {
        logInfo(tag, load.entryMessage ?: "${load.operation} called")
        val captured = load.guard.next()
        return (load.scope ?: scope).launch {
            load.state.value = UiState.Loading
            try {
                when (val outcome = execute(load.operation, load.endpoint, load.block)) {
                    is Outcome.Ok -> {
                        commitLatestSuccess(load, outcome.response, captured)
                    }

                    is Outcome.NonSuccess -> {
                        if (!load.onNonSuccess(outcome.response) && !load.guard.isStale(captured)) {
                            load.state.value =
                                UiState.Error("${load.operation} failed: ${outcome.response.status.value}")
                        }
                    }

                    is Outcome.Threw -> {
                        load.onError(outcome.e)
                        if (!load.guard.isStale(captured)) {
                            load.state.value = UiState.Error(outcome.e.message ?: "Unknown error")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(tag, "${load.operation} exception on ${load.endpoint}", e)
                load.onError(e)
                if (!load.guard.isStale(captured)) {
                    load.state.value = UiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // Success leg extracted to keep launchLatest under CognitiveComplexMethod threshold
    // (the #499 precedent): pre-decode stale bodies skip the parse, mid-decode bumps drop
    // the parsed body, current landings commit Success plus the non-suspending markers.
    private suspend fun <T> commitLatestSuccess(
        load: LatestLoad<T>,
        response: HttpResponse,
        captured: Long,
    ) {
        if (load.guard.isStale(captured)) return
        val decoded = load.decode(response)
        // #490 — recheck after the suspend decode: a bump mid-deserialization still drops
        // the stale body (its content is irrelevant to the invariant).
        if (load.guard.isStale(captured)) return
        load.state.value = UiState.Success(decoded)
        load.onCommit(decoded)
    }

    /**
     * Post-mutation retain-and-reload stateful load (#611 — [ReconcilingLoad]): the load
     * captures [ReconcilingLoad.stamp] synchronously at invocation (actions [ActionStamp.bump]
     * it alongside their synchronous KeepLast mutation). A stale pre-action snapshot commits
     * nothing and reissues instead (see [reconcileSuccess]); failure legs keep the #176 hook
     * semantics — hooks run, Error writes are gated.
     */
    @Suppress("TooGenericExceptionCaught") // #585 generic catch, CancellationException rethrows (moved #554)
    fun <T> launchReconciling(load: ReconcilingLoad<T>): Job {
        logInfo(tag, load.entryMessage ?: "${load.operation} called")
        val captured = load.stamp.capture()
        return (load.scope ?: scope).launch {
            load.state.value = UiState.Loading
            try {
                when (val outcome = execute(load.operation, load.endpoint, load.block)) {
                    is Outcome.Ok -> {
                        reconcileSuccess(load, outcome.response, captured)
                    }

                    is Outcome.NonSuccess -> {
                        if (!load.onNonSuccess(outcome.response) && !load.stamp.isStale(captured)) {
                            load.state.value =
                                UiState.Error("${load.operation} failed: ${outcome.response.status.value}")
                        }
                    }

                    is Outcome.Threw -> {
                        load.onError(outcome.e)
                        if (!load.stamp.isStale(captured)) {
                            load.state.value = UiState.Error(outcome.e.message ?: "Unknown error")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(tag, "${load.operation} exception on ${load.endpoint}", e)
                load.onError(e)
                if (!load.stamp.isStale(captured)) {
                    load.state.value = UiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // Success leg extracted like [commitLatestSuccess]: stale pre-action snapshots retain the
    // action's synchronous KeepLast write and reissue for server truth — no Success commit
    // here, not even the freshest mirror (it is already the state).
    private suspend fun <T> reconcileSuccess(
        load: ReconcilingLoad<T>,
        response: HttpResponse,
        captured: Long,
    ) {
        if (load.stamp.isStale(captured)) {
            load.reissue()
            return
        }
        val decoded = load.decode(response)
        if (load.stamp.isStale(captured)) {
            load.reissue()
            return
        }
        load.state.value = UiState.Success(decoded)
        load.onCommit(decoded)
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
    @Suppress("TooGenericExceptionCaught") // #585 generic catch, CancellationException rethrows (moved #554)
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
                when (val outcome = execute(operation, endpoint, block)) {
                    is Outcome.Ok -> transform(outcome.response)
                    is Outcome.NonSuccess -> hooks.onNonSuccess(outcome.response)
                    is Outcome.Threw -> hooks.onError(outcome.e)
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
    // Success leg extracted to keep launchStatelessGuarded under CognitiveComplexMethod
    // threshold (the #499 precedent, #637 repair): pre-decode stale bodies skip the parse,
    // mid-decode staleness drops the parsed body before the non-suspending commit.
    private suspend fun <D> commitGuardedSuccess(
        guarded: GuardedStateless<D>,
        response: HttpResponse,
    ) {
        if (guarded.stale()) return
        val decoded = guarded.decode(response)
        if (guarded.stale()) return
        guarded.commit(decoded)
    }

    @Suppress("TooGenericExceptionCaught") // #585 generic catch, CancellationException rethrows (moved #554)
    fun <D> launchStatelessGuarded(
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        guarded: GuardedStateless<D>,
    ): Job {
        logInfo(tag, guarded.entryMessage ?: "$operation called")
        return (guarded.scope ?: scope).launch {
            try {
                when (val outcome = execute(operation, endpoint, block)) {
                    is Outcome.Ok -> {
                        commitGuardedSuccess(guarded, outcome.response)
                    }

                    is Outcome.NonSuccess -> {
                        if (guarded.stale()) return@launch
                        guarded.onNonSuccess(outcome.response)
                    }

                    is Outcome.Threw -> {
                        if (guarded.stale()) return@launch
                        guarded.onError(outcome.e)
                    }
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
