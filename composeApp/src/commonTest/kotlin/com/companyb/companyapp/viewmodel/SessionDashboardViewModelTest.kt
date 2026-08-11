package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.DashboardResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.screen.bookedTimeLabel
import com.companyb.companyapp.ui.screen.centsToMoney
import com.companyb.companyapp.ui.screen.commissionLabel
import com.companyb.companyapp.ui.screen.grossIncomeCents
import com.companyb.companyapp.ui.screen.moneyToCents
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private const val DASHBOARD_PATH = "/api/branches/b1/dashboard/today"

private const val DASHBOARD_JSON =
    """{"sessions":[
        {"id":"s1","clientId":"c1","clientName":"Test Client","sessionType":"REGULAR","isWalkIn":false,
         "sessionStatus":"COMPLETED","basePrice":"2500.00","finalPrice":"2500.00","remarks":null,
         "otherConcerns":null,"bookedAt":"2026-08-12T08:00:00+08:00","nextAppointmentDate":null,
         "version":1,"isVoided":false,"practitioners":[],"concerns":[]}],
       "commission":{"amount":"200.0000","productSalesCount":1}}"""

/**
 * Session dashboard poll semantics (#147 / #97 Q5): completion-then-wait 30s cadence, silent +
 * last-successful timestamp, stale at 2 / error at 5 consecutive failures, 401 ≠ degradation,
 * 403 → forbidden + poll stop, pause/resume lifecycle. Same harness discipline as the
 * NotificationBadgeViewModelTest: runTest + StandardTestDispatcher, runCurrent/advanceTimeBy
 * (never advanceUntilIdle), vm.dispose() in finally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDashboardViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        SessionState.clear()
        SessionState.setSelectedBranch("b1", "Branch A")
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        SessionState.clear()
    }

    @Test
    fun init_fires_first_poll_and_writes_data() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            null
                        },
                    ),
                )
            try {
                runCurrent()

                assertEquals(1, requestCount)
                assertIs<UiState.Success<DashboardResponse>>(vm.dashboardState.value)
                assertNotNull(vm.lastData.value)
                assertNotNull(vm.lastUpdatedAt.value)
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value)
                assertFalse(vm.isForbidden.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun advancing_thirty_seconds_fires_second_poll() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            null
                        },
                    ),
                )
            try {
                runCurrent()
                assertEquals(1, requestCount)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()

                assertEquals(2, requestCount)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun slow_poll_delays_next_iteration_until_completion_plus_interval() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler(
                            onHit = {
                                requestCount++
                                null
                            },
                            responseDelayMs = 5_000,
                            dispatcher = StandardTestDispatcher(testScheduler),
                        ),
                    ),
                )
            try {
                // Poll 1's request is in flight (handler suspended in its delay until +5s).
                runCurrent()
                assertEquals(0, requestCount)

                // Handler completes at +5s; the loop then waits 30s → poll 2 due at +35s.
                advanceTimeBy(5_000.milliseconds)
                runCurrent()
                assertEquals(1, requestCount)

                // At +34s a WALL-CLOCK loop (start+30s) would have fired; completion-then-wait must not.
                advanceTimeBy(29_000.milliseconds)
                runCurrent()
                assertEquals(
                    1,
                    requestCount,
                    "completion-then-wait: the next poll must wait 30s AFTER the previous completed",
                )

                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(2, requestCount)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun two_consecutive_failures_flag_stale_preserving_last_data() =
        runTest(testScheduler) {
            var hits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            if (hits++ == 0) {
                                respondOk(DASHBOARD_JSON)
                            } else {
                                respondError(HttpStatusCode.BadRequest)
                            }
                        },
                    ),
                )
            try {
                runCurrent()
                assertNotNull(vm.lastData.value)
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value, "one failure is under the stale threshold")

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(DashboardPollStatus.STALE, vm.pollStatus.value)
                assertNotNull(vm.lastData.value, "stale must preserve the last successful data")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun five_consecutive_failures_escalate_to_error_status() =
        runTest(testScheduler) {
            var failures = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            if (failures++ == 0) {
                                respondOk(DASHBOARD_JSON)
                            } else {
                                respondError(HttpStatusCode.BadRequest)
                            }
                        },
                    ),
                )
            try {
                runCurrent()
                for (i in 1..4) {
                    advanceTimeBy(30_000.milliseconds)
                    runCurrent()
                }
                assertEquals(DashboardPollStatus.STALE, vm.pollStatus.value)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(DashboardPollStatus.ERRORED, vm.pollStatus.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun success_resets_failure_counter() =
        runTest(testScheduler) {
            var failures = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            if (failures++ < 2) {
                                respondError(HttpStatusCode.BadRequest)
                            } else {
                                respondOk(DASHBOARD_JSON)
                            }
                        },
                    ),
                )
            try {
                runCurrent()
                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                // Two consecutive 400s → STALE (the counting lives in the fetch hooks, not a
                // StateFlow collector — probe-proven StateFlow conflates equal Error values).
                assertEquals(DashboardPollStatus.STALE, vm.pollStatus.value)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value, "success must reset the counter")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun unauthorized_401_is_not_counted_as_degradation() =
        runTest(testScheduler) {
            var hits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            hits++
                            respondError(HttpStatusCode.Unauthorized)
                        },
                    ),
                )
            try {
                runCurrent()
                // The Ktor Auth plugin re-sends a 401'd request once (empirically proven) —
                // the handler sees 2 hits per poll; the VM must swallow both.
                assertEquals(2, hits)
                assertFalse(vm.dashboardState.value is UiState.Error, "401 must be swallowed (global auth path)")
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value)
                assertNull(vm.lastData.value)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(4, hits)
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value, "401s must never reach the stale counters")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun forbidden_403_flags_and_stops_polling() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            respondError(HttpStatusCode.Forbidden)
                        },
                    ),
                )
            try {
                runCurrent()
                assertEquals(1, requestCount)
                assertTrue(vm.isForbidden.value)

                advanceTimeBy(90_000.milliseconds)
                runCurrent()
                assertEquals(
                    1,
                    requestCount,
                    "403 must stop the poll loop — the attendance gate cannot self-heal by polling",
                )
            } finally {
                vm.pause()
            }
        }

    @Test
    fun retry_after_forbidden_resumes_polling() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            if (requestCount == 1) {
                                respondError(HttpStatusCode.Forbidden)
                            } else {
                                respondOk(DASHBOARD_JSON)
                            }
                        },
                    ),
                )
            try {
                runCurrent()
                assertTrue(vm.isForbidden.value)

                vm.retryAfterForbidden()
                runCurrent()
                assertFalse(vm.isForbidden.value)
                assertNotNull(vm.lastData.value)

                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                // hit1 = 403; hits 2-3 = the direct refresh + the restarted loop's first
                // iteration; hit 4 = the loop's next 30s iteration — polling is alive again.
                assertEquals(4, requestCount, "retry must restart the poll loop")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun pause_stops_polling_and_resume_restarts() =
        runTest(testScheduler) {
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            null
                        },
                    ),
                )
            try {
                runCurrent()
                assertEquals(1, requestCount)

                vm.pause()
                advanceTimeBy(90_000.milliseconds)
                runCurrent()
                assertEquals(1, requestCount)

                vm.resume()
                runCurrent()
                assertEquals(2, requestCount)
                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(3, requestCount)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun refresh_without_selected_branch_fails_closed_without_request() =
        runTest(testScheduler) {
            SessionState.clear()
            var requestCount = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            requestCount++
                            null
                        },
                    ),
                )
            try {
                runCurrent()
                assertEquals(0, requestCount)
                assertNull(vm.lastData.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun money_helpers_round_trip_and_truncate_scale_four() {
        assertEquals(250_000L, moneyToCents("2500.00"))
        assertEquals(20_000L, moneyToCents("200.0000"))
        assertEquals(133_333L, moneyToCents("1333.3333"))
        assertEquals(250_000L, moneyToCents("2500"))
        assertEquals(0L, moneyToCents("garbage"))
        assertEquals("2500.00", centsToMoney(250_000L))
        assertEquals("0.00", centsToMoney(0L))
        assertEquals("-1.25", centsToMoney(-125L))
    }

    @Test
    fun gross_income_excludes_voided_and_non_completed() {
        fun session(
            id: String,
            status: String,
            isVoided: Boolean,
            price: String,
        ) = DashboardSessionResponse(
            id = id,
            clientId = "c",
            clientName = "C",
            sessionType = "REGULAR",
            isWalkIn = false,
            sessionStatus = status,
            basePrice = price,
            finalPrice = price,
            remarks = null,
            otherConcerns = null,
            bookedAt = null,
            nextAppointmentDate = null,
            version = 1,
            isVoided = isVoided,
        )

        val sessions =
            listOf(
                session("s1", "COMPLETED", false, "1000.00"),
                session("s2", "COMPLETED", true, "5000.00"),
                session("s3", "PENDING", false, "2000.00"),
                session("s4", "NO_SHOW", false, "3000.00"),
            )

        assertEquals(100_000L, grossIncomeCents(sessions))
    }

    @Test
    fun commission_label_pluralizes() {
        assertEquals("from 1 product sale", commissionLabel(1))
        assertEquals("from 5 product sales", commissionLabel(5))
    }

    @Test
    fun booked_time_label_formats_manila_time() {
        assertEquals("08:00", bookedTimeLabel("2026-08-12T08:00:00+08:00"))
        assertEquals("—", bookedTimeLabel(null))
        assertEquals("—", bookedTimeLabel("garbage"))
    }

    private fun dashboardHandler(
        responseDelayMs: Long = 0,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        onHit: MockRequestHandleScope.() -> HttpResponseData? = { null },
    ): MockRequestHandler =
        {
            if (responseDelayMs > 0) {
                withContext(dispatcher) { delay(responseDelayMs) }
            }
            // The test block's response wins (error/forbidden cases); null falls back to OK.
            onHit() ?: respondOk(DASHBOARD_JSON)
        }

    private fun MockRequestHandleScope.respondOk(json: String) =
        respond(
            content = ByteReadChannel(json),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode) =
        respond(
            content = ByteReadChannel("""{"error":"boom"}"""),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
