package com.companyb.companyapp.async

import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
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
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Tests for the #611 freshness seams ([ApiCallHandler.launchLatest]'s [LoadGeneration] discard
 * policy and [ApiCallHandler.launchReconciling]'s [ActionStamp] retain-and-reload policy): a
 * load that lands after the state it was launched against moved on must not commit its
 * pre-action snapshot (the #141 resurrect class).
 *
 * - Latest (load-bumped): stale landings commit nothing and reissue nothing — no fallback
 *   data is invented, no control-flow throw holds Loading, no reissue fires. A double-initial
 *   overlap whose stale landing arrives first simply holds Loading.
 * - Reconciling (action-bumped): stale snapshots commit nothing and reissue instead; the
 *   post-action list is retained by the action's synchronous KeepLast write, never recommitted
 *   here. Stale failures keep the #176 hook semantics (hooks run, Error writes gated).
 *
 * The default-param cases pin that plain one-shot [ApiCallHandler.launch] callers keep exact
 * behavior with no guard params.
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

    // ── #611 latest-request-wins (LoadGeneration discard) ───────────────────────────
    // A superseded landing commits nothing and reissues nothing: no invented fallback data,
    // no control-flow throw, no reissue. Stale bodies skip the parse, so a malformed stale
    // body can never surface.

    @Test
    fun latest_stale_landing_skips_decode_and_commits_nothing() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val guard = LoadGeneration()
            var decodeCalls = 0

            handler.launchLatest(
                LatestLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { response: HttpResponse ->
                        decodeCalls++
                        emptyList()
                    },
                    guard = guard,
                ),
            )

            // A newer load supersedes the in-flight one before it lands.
            guard.next()
            runCurrent()

            assertEquals(
                0,
                decodeCalls,
                "a stale landing must not decode — its body is never parsed",
            )
            assertIs<UiState.Loading>(
                state.value,
                "a stale landing commits nothing — Loading stands, no fallback invented",
            )
        }

    @Test
    fun latest_current_landing_commits_decode_and_markers() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var commitCalls = 0
            var committed: List<Int>? = null

            handler.launchLatest(
                LatestLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { listOf(1, 2, 3) },
                    guard = LoadGeneration(),
                    onCommit = {
                        commitCalls++
                        committed = it
                    },
                ),
            )

            runCurrent()

            val landed = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(listOf(1, 2, 3), landed.data)
            assertEquals(1, commitCalls, "a current landing runs the marker commit once")
            assertEquals(listOf(1, 2, 3), committed)
        }

    @Test
    fun latest_bump_mid_decode_drops_stale_body() =
        runTest(testScheduler) {
            // #490 — the guard is rechecked AFTER the suspend decode returns: a newer load
            // starting mid-deserialization must still drop the stale body.
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val guard = LoadGeneration()
            var commitCalls = 0

            handler.launchLatest(
                LatestLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = {
                        guard.next()
                        listOf(1, 2, 3)
                    },
                    guard = guard,
                    onCommit = { commitCalls++ },
                ),
            )

            runCurrent()

            assertIs<UiState.Loading>(
                state.value,
                "a body that went stale mid-decode must not commit",
            )
            assertEquals(0, commitCalls, "a stale body must not run the marker commit")
        }

    @Test
    fun latest_malformed_stale_body_never_surfaces() =
        runTest(testScheduler) {
            // A stale 2xx whose body would fail parsing: skipped before the parse, so no Error
            // may surface for a response the caller discards.
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val guard = LoadGeneration()

            handler.launchLatest(
                LatestLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { error("must not parse a stale body") },
                    guard = guard,
                ),
            )

            guard.next()
            runCurrent()

            assertIs<UiState.Loading>(
                state.value,
                "a malformed stale body must leave Loading, never Error",
            )
        }

    @Test
    fun latest_opposite_completion_orders_newest_wins() =
        runTest(testScheduler) {
            // Two overlapping loads; the FIRST launched lands LAST with divergent data.
            val releaseFirst = CompletableDeferred<Unit>()
            var gets = 0
            val apiClient =
                mockApiClient { _ ->
                    gets++
                    if (gets == 1) {
                        releaseFirst.await()
                    }
                    respond(
                        content = ByteReadChannel("[$gets]"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<String>>(UiState.Idle)
            val guard = LoadGeneration()

            fun latestLoad(): LatestLoad<String> =
                LatestLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { it.bodyAsText() },
                    guard = guard,
                )

            handler.launchLatest(latestLoad())
            runCurrent()
            handler.launchLatest(latestLoad())
            runCurrent()
            assertEquals(
                UiState.Success("[2]"),
                state.value,
                "the newer load lands first and commits",
            )

            releaseFirst.complete(Unit)
            runCurrent()

            assertEquals(
                UiState.Success("[2]"),
                state.value,
                "the stale first landing must not overwrite the newer commit",
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
    fun latest_stale_non_success_response_does_not_commit_error() =
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
            val guard = LoadGeneration()
            var hookCalls = 0

            // Hold the first non-success response in flight, then supersede it and write the
            // newer Success before releasing it. #176: hooks still run, but a superseded
            // failure writes NO Error over the moved-on Success.
            val job =
                handler.launchLatest(
                    LatestLoad(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = { apiClient.httpClient.get("/api/items") },
                        decode = { emptyList() },
                        guard = guard,
                        onNonSuccess = {
                            hookCalls++
                            false
                        },
                    ),
                )
            runCurrent()
            assertEquals(
                true,
                requestStarted.isCompleted,
                "the non-success response must be in flight before the newer load",
            )
            guard.next()
            state.value = UiState.Success(listOf(7))
            releaseResponse.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(1, hookCalls, "the onNonSuccess hook still runs on a stale failure")
            val staleState =
                assertIs<UiState.Success<List<Int>>>(
                    state.value,
                    "a stale non-success landing must leave the moved-on Success, not write Error",
                )
            assertEquals(listOf(7), staleState.data)
        }

    @Test
    fun latest_stale_exception_does_not_commit_error_but_runs_onError() =
        runTest(testScheduler) {
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val guard = LoadGeneration()
            var onErrorCalls = 0
            var decodeCalls = 0
            val requestStarted = CompletableDeferred<Unit>()
            val releaseFailure = CompletableDeferred<Unit>()

            // Hold the exception in flight, then supersede it before releasing it. #176 gates
            // the Error WRITE only — the hook still runs.
            val job =
                handler.launchLatest(
                    LatestLoad(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = {
                            requestStarted.complete(Unit)
                            releaseFailure.await()
                            error("boom")
                        },
                        decode = {
                            decodeCalls++
                            emptyList()
                        },
                        onError = { onErrorCalls++ },
                        guard = guard,
                    ),
                )
            runCurrent()
            assertEquals(true, requestStarted.isCompleted, "the failure must be in flight before superseding")
            guard.next()
            releaseFailure.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(1, onErrorCalls, "the onError hook must still run on a stale failure")
            assertEquals(0, decodeCalls, "a throwing block must not run decode")
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

            // Plain one-shot — no guard exists, so the non-success landing is CURRENT:
            // a genuine failure must surface (the negative pin).
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

            // Plain one-shot — no guard exists, so the exception is CURRENT: a genuine
            // failure must surface.
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

    // ── #611 post-mutation retain-and-reload (ActionStamp reconciliation) ──────────────
    // A stale pre-action snapshot commits nothing and reissues instead; the post-action list
    // stays retained (the action's synchronous KeepLast write — simulated here by direct
    // state writes). Stale failures keep the #176 hook semantics.

    @Test
    fun reconciling_current_success_commits_without_reissue() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            var reissues = 0

            handler.launchReconciling(
                ReconcilingLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { listOf(1, 2, 3) },
                    stamp = ActionStamp(),
                    reissue = { reissues++ },
                ),
            )
            runCurrent()

            val landed = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(listOf(1, 2, 3), landed.data)
            assertEquals(0, reissues, "a current landing must not reissue")
        }

    @Test
    fun reconciling_stale_success_reissues_without_committing() =
        runTest(testScheduler) {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            val apiClient =
                mockApiClient { _ ->
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                    respond(
                        content = ByteReadChannel("""[]"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val stamp = ActionStamp()
            var decodeCalls = 0
            var reissues = 0

            val job =
                handler.launchReconciling(
                    ReconcilingLoad(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = { apiClient.httpClient.get("/api/items") },
                        decode = { _: HttpResponse ->
                            decodeCalls++
                            listOf(9)
                        },
                        stamp = stamp,
                        reissue = { reissues++ },
                    ),
                )
            runCurrent()
            assertEquals(true, requestStarted.isCompleted, "the load must be in flight before the action")

            // An action lands while the load is in flight: synchronous post-action write plus
            // the stamp bump (the KeepLast mutation shape).
            stamp.bump()
            state.value = UiState.Success(listOf(7))
            releaseResponse.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(0, decodeCalls, "an already-stale body must never be parsed")
            assertEquals(1, reissues, "a stale pre-action snapshot must reissue for server truth")
            val retained = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(
                listOf(7),
                retained.data,
                "the post-action list is retained — never recommitted, never resurrected",
            )
        }

    @Test
    fun reconciling_action_mid_decode_reissues_and_drops_body() =
        runTest(testScheduler) {
            val apiClient = mockApiClient { respond200() }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val stamp = ActionStamp()
            var reissues = 0

            handler.launchReconciling(
                ReconcilingLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = {
                        stamp.bump()
                        listOf(1, 2, 3)
                    },
                    stamp = stamp,
                    reissue = { reissues++ },
                ),
            )
            runCurrent()

            assertEquals(1, reissues, "an action mid-decode must reissue instead of committing")
            assertIs<UiState.Loading>(
                state.value,
                "the mid-decode pre-action body must not commit over the moved-on surface",
            )
        }

    @Test
    fun reconciling_stale_failure_runs_hook_without_error_or_reissue() =
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
            val stamp = ActionStamp()
            var hookCalls = 0
            var reissues = 0

            val job =
                handler.launchReconciling(
                    ReconcilingLoad(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = { apiClient.httpClient.get("/api/items") },
                        decode = { emptyList() },
                        stamp = stamp,
                        reissue = { reissues++ },
                        onNonSuccess = {
                            hookCalls++
                            false
                        },
                    ),
                )
            runCurrent()
            assertEquals(
                true,
                requestStarted.isCompleted,
                "the non-success response must be in flight before the action",
            )
            stamp.bump()
            state.value = UiState.Success(listOf(7))
            releaseResponse.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(1, hookCalls, "the onNonSuccess hook still runs on a stale failure")
            assertEquals(0, reissues, "a stale failure carries no snapshot — nothing to reconverge")
            val retained = assertIs<UiState.Success<List<Int>>>(state.value)
            assertEquals(listOf(7), retained.data)
        }

    @Test
    fun reconciling_failed_reissue_surfaces_error() =
        runTest(testScheduler) {
            // Failed reconciliation: the stale landing reissues, and the reissued GET fails
            // while current — the reissue's Error stands (the stale snapshot itself committed
            // nothing at either step).
            val releaseStale = CompletableDeferred<Unit>()
            var gets = 0
            val apiClient =
                mockApiClient { _ ->
                    gets++
                    if (gets == 1) {
                        releaseStale.await()
                        respond(
                            content = ByteReadChannel("""[]"""),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        // 400, not 500: the client's HttpRequestRetry re-issues 5xx inside the
                        // same landing (a retry is not a newer load) and would inflate the count.
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            val handler = ApiCallHandler(CoroutineScope(Dispatchers.Main), "Test")
            val state = MutableStateFlow<UiState<List<Int>>>(UiState.Idle)
            val stamp = ActionStamp()
            var reissues = 0

            fun reissueLoad() {
                reissues++
                handler.launchReconciling(
                    ReconcilingLoad(
                        state = state,
                        operation = "load",
                        endpoint = "GET /api/items",
                        block = { apiClient.httpClient.get("/api/items") },
                        decode = { emptyList() },
                        stamp = stamp,
                        reissue = {},
                    ),
                )
            }

            handler.launchReconciling(
                ReconcilingLoad(
                    state = state,
                    operation = "load",
                    endpoint = "GET /api/items",
                    block = { apiClient.httpClient.get("/api/items") },
                    decode = { emptyList() },
                    stamp = stamp,
                    reissue = ::reissueLoad,
                ),
            )
            runCurrent()

            stamp.bump()
            state.value = UiState.Success(listOf(7))
            releaseStale.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, reissues, "the stale snapshot must trigger exactly one reissue")
            assertEquals(2, gets, "stale GET plus reissued GET")
            assertIs<UiState.Error>(
                state.value,
                "the failed reissue lands current — its Error stands",
            )
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
