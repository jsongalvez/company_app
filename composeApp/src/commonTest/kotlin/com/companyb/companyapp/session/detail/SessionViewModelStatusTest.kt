package com.companyb.companyapp.session.detail

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.AddPractitionerRequest
import com.companyb.companyapp.contracts.session.SessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.UpdateSessionStatusRequest
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val SESSION_ID = "s1"

private const val STATUS_OK_JSON =
    """{"id":"s1","clientId":"c1","branchDayId":"bd1","requestedPractitionerId":null,
        "sessionType":"REGULAR","isWalkIn":false,"sessionStatus":"COMPLETED",
        "basePrice":"2500.00","finalPrice":"2500.00","remarks":null,"otherConcerns":null,
        "bookedAt":null,"nextAppointmentDate":null,"version":4,"concerns":[]}"""

private const val DAY_OPEN_JSON = """{"branchDayId":"b1","status":"OPEN"}"""

private fun statusRequest() = UpdateSessionStatusRequest(status = SessionStatus.COMPLETED, version = 3)

/**
 * #675 — the detail status transition: success lands the patched row, a double dispatch
 * sends once (stale-version second tap would 409 against the just-committed row), a 409
 * raises the handled-conflict flag instead of a generic error, other failures land
 * Error, and the day-status read decodes behind the action model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelStatusTest {
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
    fun status_update_success_lands_patched_row() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = { request ->
                requestCount++
                assertEquals("/api/sessions/$SESSION_ID/status", request.url.encodedPath)
                respondOk(STATUS_OK_JSON)
            }
            val vm = SessionViewModel(mockApiClient(handler))

            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()

            assertEquals(1, requestCount)
            val state = vm.statusResult.value
            assertIs<UiState.Success<SessionResponse>>(state)
            assertEquals(SessionStatus.COMPLETED, state.data.sessionStatus)
            assertEquals(4, state.data.version)
            assertFalse(vm.statusConflict.value)
        }

    @Test
    fun back_to_back_status_updates_dispatch_once() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(STATUS_OK_JSON)
            }
            val vm = SessionViewModel(mockApiClient(handler))

            // The Loading pre-set holds from the caller's frame (the #135 pattern) — a
            // double click must not send a second PATCH with the stale version.
            vm.updateSessionStatus(SESSION_ID, statusRequest())
            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()

            assertEquals(1, requestCount)
        }

    @Test
    fun version_conflict_raises_handled_flag_instead_of_error() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { respondError(HttpStatusCode.Conflict) }
            val vm = SessionViewModel(mockApiClient(handler))

            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()

            assertTrue(vm.statusConflict.value, "409 is handled, not generic-error surfaced")
            assertTrue(vm.statusResult.value is UiState.Idle, "handled legs skip the Error write")

            vm.clearStatusConflict()
            assertFalse(vm.statusConflict.value)
        }

    @Test
    fun non_conflict_failure_lands_error_without_conflict_flag() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { respondError(HttpStatusCode.Forbidden) }
            val vm = SessionViewModel(mockApiClient(handler))

            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()

            assertTrue(vm.statusResult.value is UiState.Error)
            assertFalse(vm.statusConflict.value)

            vm.consumeStatusResult()
            assertTrue(vm.statusResult.value is UiState.Idle)
        }

    @Test
    fun status_error_stays_until_next_dispatch_or_consume() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                if (requestCount == 1) {
                    respondError(HttpStatusCode.Forbidden)
                } else {
                    respondOk(STATUS_OK_JSON)
                }
            }
            val vm = SessionViewModel(mockApiClient(handler))

            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()
            assertTrue(vm.statusResult.value is UiState.Error, "no auto-consume: the retry row keeps it")

            // Retry redispatches (Error is not Loading, so the double-tap guard passes)
            // and the fresh landing replaces the sticky failure.
            vm.updateSessionStatus(SESSION_ID, statusRequest())
            runCurrent()

            assertEquals(2, requestCount)
            assertIs<UiState.Success<SessionResponse>>(vm.statusResult.value)
        }

    @Test
    fun day_status_read_decodes_behind_the_action_model() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                assertEquals("/api/branches/b1/today", request.url.encodedPath)
                respondOk(DAY_OPEN_JSON)
            }
            val vm = SessionViewModel(mockApiClient(handler))

            vm.loadDayStatus("b1")
            runCurrent()

            val state = vm.dayStatus.value
            assertIs<UiState.Success<DayStatus>>(state)
            assertEquals(DayStatus.OPEN, state.data)
        }

    @Test
    fun back_to_back_adds_dispatch_once() =
        runTest(testScheduler) {
            var requestCount = 0
            val handler: MockRequestHandler = {
                requestCount++
                respondOk(
                    """{"id":"sp1","sessionId":"s1","practitionerId":"u1","remarks":null,"slotAtTime":1}""",
                )
            }
            val vm = SessionViewModel(mockApiClient(handler))

            // #675 — the same caller-frame guard covers membership (duplicate adds are
            // server-idempotent, but the second tap must not even send).
            val request =
                AddPractitionerRequest(
                    id = "k1",
                    practitionerId = "u1",
                )
            vm.addPractitioner(SESSION_ID, request)
            vm.addPractitioner(SESSION_ID, request)
            runCurrent()

            assertEquals(1, requestCount)
        }
}
