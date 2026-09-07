package com.companyb.companyapp.async

import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for the #165 handler-level stale-substitution guard ([ApiCallHandler.launch]'s
 * stamp/fallback params): a load that lands after the state it was launched against moved on
 * must not commit its pre-action snapshot (the #141 resurrect class) — the resurrect-invariant
 * formerly hand-rolled at NotificationVM.loadUnreadNotifications + ReliefInviteVM.loadReceived.
 * The handler reads [ApiCallHandler.launch]'s stamp() twice — at launch invocation (captured)
 * and at landing — and commits transform(response) only when the two reads agree, else
 * fallback().
 *
 * The default-param cases pin that every existing handler caller (which passes no guard
 * params) keeps the exact pre-#165 behavior: the guard never diverges — a constant stamp
 * always agrees, so transform always commits and the fallback is never invoked.
 *
 * The stateless section covers [ApiCallHandler.launchStateless]'s hooks + the #173
 * stale-gate (`stale` skips all three hooks on a superseded landing — the #165 class's
 * stateless leg, replacing the hand-rolled generation guards).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiCallHandlerTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun MockRequestHandleScope.respond200() =
        respond(
            content = ByteReadChannel("""[]"""),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    @Test
    fun stale_landing_commits_fallback_not_transform() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var stamp = 0L
            var transformCalls = 0

            // Launch with the guard: stamp() is read at launch invocation, then again when the
            // response lands.
            handler.launch(
                LaunchRequest(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    transform = { response: HttpResponse ->
                        transformCalls++
                        emptyList()
                    },
                    stamp = { stamp },
                    fallback = { listOf(9) },
                ),
            )

            // A concurrent action bumps the stamp while the load is in flight: the landing is
            // stale, so the handler must commit the fallback, never the (undeserialized)
            // response's transform.
            stamp = 1
            runCurrent()

            val committed = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(listOf(9), committed.data)
            assertEquals(
                0,
                transformCalls,
                "a stale landing must not call transform — its body is never deserialized",
            )
        }

    @Test
    fun current_landing_commits_transform_not_fallback() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val stamp = 0L
            var fallbackCalls = 0

            handler.launch(
                LaunchRequest(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    transform = { listOf(1, 2, 3) },
                    stamp = { stamp },
                    fallback = {
                        fallbackCalls++
                        emptyList()
                    },
                ),
            )

            runCurrent()

            val committed = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(listOf(1, 2, 3), committed.data)
            assertEquals(0, fallbackCalls, "a current landing must commit the transform")
        }

    @Test
    fun stamp_bump_mid_transform_drops_stale_body() =
        runTest(testScheduler) {
            // #490 — the stamp is rechecked AFTER the suspend transform returns: a generation
            // bump mid-deserialization must still drop the stale body for the fallback.
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var stamp = 0L

            handler.launch(
                LaunchRequest(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    transform = {
                        stamp = 1
                        listOf(1, 2, 3)
                    },
                    stamp = { stamp },
                    fallback = { listOf(9) },
                ),
            )

            runCurrent()

            val committed = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(
                listOf(9),
                committed.data,
                "a body deserialized after a mid-transform stamp bump must not commit",
            )
        }

    @Test
    fun default_params_commit_transform_without_guard() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<String>>(UiState.Idle)

            // No guard params — the historical shape: transform always commits on success.
            handler.launch(
                state = state,
                operation = "load",
                endpoint = "GET /api/items",
                block = { apiClient.httpClient.get("/api/items") },
                transform = { "data" },
            )

            runCurrent()

            assertEquals(UiState.Success("data"), state.value)
        }

    @Test
    fun stale_non_success_response_does_not_commit_error() =
        runTest(testScheduler) {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            val apiClient =
                mockApiClient { _ ->
                    if (requestStarted.complete(Unit)) {
                        releaseResponse.await()
                    }
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)

            // Hold the first non-success response in flight, then move the stamp and write the
            // newer action Success before releasing it. #176 gates the Error WRITE only — a
            // superseded failure writes NO Error over that moved-on state (and never fallback).
            var stamp = 0L
            var fallbackCalls = 0
            val job =
                handler.launch(
                    LaunchRequest(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = { apiClient.httpClient.get("/api/items") },
                        transform = { emptyList() },
                        stamp = { stamp },
                        fallback = {
                            fallbackCalls++
                            emptyList()
                        },
                    ),
                )
            runCurrent()
            assertEquals(
                true,
                requestStarted.isCompleted,
                "the non-success response must be in flight before the stamp flip",
            )
            stamp = 1
            state.value = UiState.Success(listOf(7))
            releaseResponse.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(0, fallbackCalls, "a failure carries no data — fallback substitutes nothing")
            val staleState =
                assertIs<UiState.Success<List<Int>>>(
                    state.value,
                    "a stale non-success landing must leave the moved-on Success, not write Error",
                )
            assertEquals(listOf(7), staleState.data)
        }

    @Test
    fun stale_exception_does_not_commit_error_but_runs_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var onErrorCalls = 0
            var transformCalls = 0
            var stamp = 0L
            val requestStarted = CompletableDeferred<Unit>()
            val releaseFailure = CompletableDeferred<Unit>()

            // Hold the exception in flight, then move the stamp before releasing it. #176 gates
            // the Error WRITE only — the hook still runs after the real ordering flip.
            val job =
                handler.launch(
                    LaunchRequest(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = {
                            requestStarted.complete(Unit)
                            releaseFailure.await()
                            error("boom")
                        },
                        transform = {
                            transformCalls++
                            emptyList()
                        },
                        onError = { onErrorCalls++ },
                        stamp = { stamp },
                        fallback = { emptyList() },
                    ),
                )
            runCurrent()
            assertEquals(true, requestStarted.isCompleted, "the failure must be in flight before the stamp flip")
            stamp = 1
            releaseFailure.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(1, onErrorCalls, "the onError hook must still run on a stale failure")
            assertEquals(0, transformCalls, "a throwing block must not run transform")
            assertIs<UiState.Loading>(
                state.value,
                "a stale exception must not write Error onto the moved-on surface",
            )
        }

    @Test
    fun non_success_response_commits_error_when_current() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient { _ ->
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)

            // Default (constant) stamp — always agrees, so the non-success landing is CURRENT:
            // the gate must not swallow a genuine failure (the #176 negative of the new pin).
            val job =
                handler.launch(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    transform = { emptyList() },
                )
            job.join()

            assertIs<UiState.Error>(state.value)
        }

    @Test
    fun exception_commits_error_when_current() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var onErrorCalls = 0

            // Default (constant) stamp — always agrees, so the exception is CURRENT: the gate
            // must not swallow a genuine failure.
            handler.launch(
                LaunchRequest(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { error("boom") },
                    transform = { emptyList() },
                    onError = { onErrorCalls++ },
                ),
            )
            runCurrent()

            assertEquals(1, onErrorCalls)
            assertIs<UiState.Error>(state.value)
        }

    // ── #168 state-less launch ────────────────────────────────────────────────────────
    // The state-less variant writes no UiState at all — its observable contract is which of
    // transform / onNonSuccess / onError runs, matching the [ApiCallHandler.launch] status
    // branches minus the state assignments.

    @Test
    fun stateless_success_runs_transform() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var transformCalls = 0
            var onNonSuccessCalls = 0

            handler.launchStateless(
                operation = "load",
                endpoint = "GET /api/items",
                block = { apiClient.httpClient.get("/api/items") },
                transform = { transformCalls++ },
                hooks =
                    StatelessHooks(
                        onNonSuccess = { onNonSuccessCalls++ },
                    ),
            )

            runCurrent()

            assertEquals(1, transformCalls, "a 2xx must run transform")
            assertEquals(0, onNonSuccessCalls, "a 2xx must not run onNonSuccess")
        }

    @Test
    fun stateless_non_success_runs_onNonSuccess() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient { _ ->
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var transformCalls = 0
            var onNonSuccessCalls = 0

            // Non-2xx completes off the test scheduler (the #93 class idiom) — join.
            val job =
                handler.launchStateless(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    transform = { transformCalls++ },
                    hooks =
                        StatelessHooks(
                            onNonSuccess = { onNonSuccessCalls++ },
                        ),
                )
            job.join()

            assertEquals(0, transformCalls, "a non-2xx must not run transform")
            assertEquals(1, onNonSuccessCalls, "a non-2xx must run onNonSuccess")
        }

    @Test
    fun stateless_exception_runs_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var onErrorCalls = 0
            var transformCalls = 0

            handler.launchStateless(
                operation = "load",
                endpoint = "GET /api/items",
                block = { error("boom") },
                transform = { transformCalls++ },
                hooks =
                    StatelessHooks(
                        onError = { onErrorCalls++ },
                    ),
            )

            runCurrent()

            assertEquals(0, transformCalls, "a throwing block must not run transform")
            assertEquals(1, onErrorCalls, "a throwing block must run onError")
        }

    @Test
    fun stateless_cancellation_rethrows_without_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var onErrorCalls = 0

            val job =
                handler.launchStateless(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = {
                        // Suspend forever so the launch is genuinely in flight when cancelled.
                        kotlinx.coroutines.awaitCancellation()
                    },
                    transform = {},
                    hooks =
                        StatelessHooks(
                            onError = { onErrorCalls++ },
                        ),
                )

            // Drive the coroutine into awaitCancellation BEFORE cancelling — otherwise cancel()
            // hits a not-yet-started job and the test passes vacuously (the ReliefInviteViewModelTest
            // :221 precedent: "without the delay, cancel() would hit a dead job").
            runCurrent()
            // #113 invariant: cancellation isn't a request failure — it must rethrow, never
            // surface as an error hook invocation.
            job.cancel()
            runCurrent()

            assertEquals(0, onErrorCalls, "cancellation must not run onError")
        }

    // ── #528 guarded decode/commit boundary ──────────────────────────────────────────
    // The generation-guarded stateless surface splits suspend decode from the non-suspending
    // commit: already-stale bodies skip the parse, bodies that go stale mid-decode never
    // commit, and the failure legs are non-suspending commits closed by a pre-commit read.
    // A generic post-callback check around a combined decode+commit lambda cannot retract the
    // lambda's side effects — hence the split.

    @Test
    fun guarded_current_success_decodes_and_commits() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var decodeCalls = 0
            var commitCalls = 0

            val job =
                handler.launchStatelessGuarded(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    guarded =
                        GuardedStateless(
                            decode = {
                                decodeCalls++
                                listOf(1)
                            },
                            commit = { commitCalls++ },
                            stale = { false },
                        ),
                )
            job.join()

            assertEquals(1, decodeCalls, "a current landing must decode")
            assertEquals(1, commitCalls, "a current landing must commit")
        }

    @Test
    fun guarded_stale_before_decode_skips_parse() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var decodeCalls = 0
            var commitCalls = 0
            var stale = false

            handler.launchStatelessGuarded(
                operation = "load",
                endpoint = "GET /api/items",
                block = { apiClient.httpClient.get("/api/items") },
                guarded =
                    GuardedStateless(
                        decode = {
                            decodeCalls++
                            listOf(1)
                        },
                        commit = { commitCalls++ },
                        stale = { stale },
                    ),
            )

            // Queued on the test scheduler: flipping before runCurrent makes the pre-decode
            // read see stale — the parse is skipped, not just the commit.
            stale = true
            runCurrent()

            assertEquals(0, decodeCalls, "an already-stale body must never be deserialized")
            assertEquals(0, commitCalls, "an already-stale body must never commit")
        }

    @Test
    fun guarded_bump_mid_decode_drops_stale_body() =
        runTest(testScheduler) {
            // #528 core: the decoder suspends after HTTP success but before body completion;
            // the generation bumps during that window. The old body must not commit.
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val decodeEntered = CompletableDeferred<Unit>()
            val releaseDecode = CompletableDeferred<Unit>()
            var stale = false
            var commitCalls = 0
            var committed: List<Int>? = null

            val job =
                handler.launchStatelessGuarded(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    guarded =
                        GuardedStateless(
                            decode = {
                                decodeEntered.complete(Unit)
                                releaseDecode.await()
                                listOf(1, 2, 3)
                            },
                            commit = {
                                commitCalls++
                                committed = it
                            },
                            stale = { stale },
                        ),
                )
            runCurrent()
            assertEquals(true, decodeEntered.isCompleted, "decode must be suspended before the flip")
            stale = true
            releaseDecode.complete(Unit)
            job.join()

            assertEquals(0, commitCalls, "a body that went stale mid-decode must not commit")
            assertEquals(null, committed, "no stale payload may reach the commit")
        }

    @Test
    fun guarded_stale_non_success_skips_commit() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient { _ ->
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var commitCalls = 0
            var failureCalls = 0

            // Non-2xx completes off the test scheduler (the #93 class idiom) — join.
            val job =
                handler.launchStatelessGuarded(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    guarded =
                        GuardedStateless(
                            decode = {
                                commitCalls++
                                Unit
                            },
                            commit = {},
                            // Structurally stale before any landing — the gate must skip the hook.
                            stale = { true },
                            onNonSuccess = { failureCalls++ },
                        ),
                )
            job.join()

            assertEquals(0, failureCalls, "a stale non-success landing must skip onNonSuccess")
            assertEquals(0, commitCalls, "a stale non-success landing must not decode")
        }

    @Test
    fun guarded_stale_exception_skips_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var decodeCalls = 0
            var onErrorCalls = 0
            var stale = false

            handler.launchStatelessGuarded(
                operation = "load",
                endpoint = "GET /api/items",
                block = { error("boom") },
                guarded =
                    GuardedStateless(
                        decode = {
                            decodeCalls++
                            Unit
                        },
                        commit = {},
                        stale = { stale },
                        onError = { onErrorCalls++ },
                    ),
            )

            // The generation bumps while the request is queued — the failure lands stale and
            // must not surface on the moved-on surface.
            stale = true
            runCurrent()

            assertEquals(0, onErrorCalls, "a stale exception must skip onError")
            assertEquals(0, decodeCalls, "a stale exception must not decode")
        }

    @Test
    fun guarded_current_non_success_runs_commit() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient { _ ->
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var failureCalls = 0

            val job =
                handler.launchStatelessGuarded(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    guarded =
                        GuardedStateless(
                            decode = { Unit },
                            commit = {},
                            stale = { false },
                            onNonSuccess = { failureCalls++ },
                        ),
                )
            job.join()

            assertEquals(1, failureCalls, "a current non-success must run onNonSuccess")
        }

    @Test
    fun guarded_current_exception_runs_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var onErrorCalls = 0

            handler.launchStatelessGuarded(
                operation = "load",
                endpoint = "GET /api/items",
                block = { error("boom") },
                guarded =
                    GuardedStateless(
                        decode = { Unit },
                        commit = {},
                        stale = { false },
                        onError = { onErrorCalls++ },
                    ),
            )
            runCurrent()

            assertEquals(1, onErrorCalls, "a current exception must run onError")
        }

    @Test
    fun guarded_cancellation_rethrows_without_commit() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            var commitCalls = 0
            var onErrorCalls = 0

            val job =
                handler.launchStatelessGuarded(
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = {
                        kotlinx.coroutines.awaitCancellation()
                    },
                    guarded =
                        GuardedStateless(
                            decode = { Unit },
                            commit = { commitCalls++ },
                            stale = { false },
                            onError = { onErrorCalls++ },
                        ),
                )

            runCurrent()
            job.cancel()
            runCurrent()

            assertEquals(0, commitCalls, "cancellation must not commit")
            assertEquals(0, onErrorCalls, "cancellation must not run onError")
        }
}
