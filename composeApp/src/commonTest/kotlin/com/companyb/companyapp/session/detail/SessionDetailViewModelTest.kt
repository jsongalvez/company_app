package com.companyb.companyapp.session.detail

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

private const val SESSION_ID = "s1"

private const val SESSION_DETAIL_JSON =
    """{"id":"s1","clientId":"c1","clientName":"Test Client","sessionType":"REGULAR","isWalkIn":false,
        "sessionStatus":"COMPLETED","basePrice":"2500.00","finalPrice":"2500.00","remarks":null,
        "otherConcerns":null,"bookedAt":"2026-08-12T08:00:00+08:00","nextAppointmentDate":null,
        "version":1,"isVoided":false,"practitioners":[],"concerns":[]}"""

private fun testRow() =
    DashboardSessionResponse(
        id = SESSION_ID,
        clientId = "c1",
        clientName = "Test Client",
        sessionType = com.companyb.companyapp.domain.SessionType.REGULAR,
        isWalkIn = false,
        sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        basePrice = "2500.00",
        finalPrice = "2500.00",
        remarks = null,
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
        version = 1,
        isVoided = false,
    )

/**
 * #152 one-shot fetch semantics (#151 Q7): fetch ONLY when the route arrived without a row
 * (the notifications path); the dashboard path (row present) never dispatches; double-fire
 * guarded against recomposition refires; explicit Retry re-dispatches after an error; the
 * in-flight guard blocks concurrent retries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
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

    private fun MockRequestHandleScope.respondOk(json: String): HttpResponseData =
        respond(
            content = ByteReadChannel(json),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode): HttpResponseData =
        respond(
            content = ByteReadChannel("""{"error":"boom"}"""),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    @Test
    fun dashboard_path_row_present_never_fetches() =
        runTest(testScheduler) {
            var requestCount = 0
            val row = testRow()
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(SESSION_DETAIL_JSON)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = row,
                )
            vm.loadIfNeeded()
            runCurrent()

            assertEquals(0, requestCount, "the dashboard path passes the enriched row — zero requests")
            val state = vm.detail.value
            assertIs<UiState.Success<DashboardSessionResponse>>(state)
            assertEquals(row, state.data)
        }

    @Test
    fun notifications_path_null_row_fetches_once_and_lands_data() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = { request ->
                requestCount++
                assertEquals("/api/sessions/$SESSION_ID", request.url.encodedPath)
                respondOk(SESSION_DETAIL_JSON)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = null,
                )
            vm.loadIfNeeded()
            runCurrent()

            assertEquals(1, requestCount)
            val state = vm.detail.value
            assertIs<UiState.Success<DashboardSessionResponse>>(state)
            assertEquals("Test Client", state.data.clientName)
        }

    @Test
    fun back_to_back_load_calls_dispatch_single_request() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(SESSION_DETAIL_JSON)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = null,
                )
            // The synchronous fetchStarted flag must hold from the caller's frame (the
            // #135 double-tap pattern) — recomposition/rotation refires loadIfNeeded
            // while the entry-scoped VM survives.
            vm.loadIfNeeded()
            vm.loadIfNeeded()
            runCurrent()

            assertEquals(1, requestCount)
        }

    @Test
    fun not_found_lands_error_and_retry_redispatches() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                if (requestCount == 1) {
                    // 404 for both non-bearer and missing (#151 Q5) — one fallback.
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respondOk(SESSION_DETAIL_JSON)
                }
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = null,
                )
            vm.loadIfNeeded()
            runCurrent()

            assertEquals(1, requestCount)
            assertTrue(vm.detail.value is UiState.Error)

            vm.retry()
            runCurrent()

            assertEquals(2, requestCount, "explicit retry always re-dispatches")
            assertIs<UiState.Success<DashboardSessionResponse>>(vm.detail.value)
        }

    @Test
    fun error_then_load_if_needed_without_retry_does_not_redispatches() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondError(HttpStatusCode.NotFound)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = null,
                )
            vm.loadIfNeeded()
            runCurrent()
            assertEquals(1, requestCount)
            assertTrue(vm.detail.value is UiState.Error)

            // A recomposition refire of loadIfNeeded must not silently retry — the Retry
            // button is the sanctioned path (an auto-refire would hide the 404 fallback).
            vm.loadIfNeeded()
            runCurrent()
            assertEquals(1, requestCount)
        }

    @Test
    fun back_to_back_retries_dispatch_one_guarded_retry() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                if (requestCount == 1) {
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respondOk(SESSION_DETAIL_JSON)
                }
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = null,
                )
            // Drive the VM into Error first — that is the state the Retry button actually
            // renders in, and the state two rapid taps leave from before recomposition hides
            // the button. The synchronous inFlight flag must hold from the caller's frame —
            // two concurrent GETs could land out of order (the stale-response overwrite class).
            vm.loadIfNeeded()
            runCurrent()
            assertTrue(vm.detail.value is UiState.Error)

            vm.retry()
            vm.retry()
            runCurrent()

            assertEquals(2, requestCount, "one initial load + one guarded retry")
            assertIs<UiState.Success<DashboardSessionResponse>>(vm.detail.value)
        }

    @Test
    fun refresh_refetches_even_when_seeded_from_the_dashboard_path() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(SESSION_DETAIL_JSON)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = testRow(),
                )
            vm.loadIfNeeded()
            runCurrent()
            assertEquals(0, requestCount, "seeded path never fetches on entry")

            // #382 — the pane's post-mutation authoritative reload works regardless of how
            // the row arrived; retry() would no-op here by design.
            vm.refresh()
            runCurrent()

            assertEquals(1, requestCount)
            assertIs<UiState.Success<DashboardSessionResponse>>(vm.detail.value)
        }

    @Test
    fun back_to_back_refreshes_dispatch_one_guarded_reload() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(SESSION_DETAIL_JSON)
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = testRow(),
                )
            // The in-flight guard holds from the caller's frame: two rapid reloads (mutation
            // + conflict landing racing) must not stack concurrent GETs.
            vm.refresh()
            vm.refresh()
            runCurrent()

            assertEquals(1, requestCount)
        }

    @Test
    fun refresh_failure_keeps_the_rendered_row_instead_of_error() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                if (requestCount == 1) {
                    // The bearer-only read 404s for a seeded dashboard-push row whose caller
                    // holds no notification (#152 gate) — the rendered detail must survive.
                    respondError(HttpStatusCode.NotFound)
                } else {
                    respondOk(SESSION_DETAIL_JSON)
                }
            }
            val vm =
                SessionDetailViewModel(
                    mockApiClient(handler),
                    SESSION_ID,
                    initialRow = testRow(),
                )
            vm.refresh()
            runCurrent()

            assertEquals(1, requestCount)
            val state = vm.detail.value
            assertIs<UiState.Success<DashboardSessionResponse>>(state)
            assertEquals(testRow(), state.data)
        }
}
