package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.ui.screen.bookedTimeLabel
import com.companyb.companyapp.ui.screen.centsToMoney
import com.companyb.companyapp.ui.screen.commissionLabel
import com.companyb.companyapp.ui.screen.grossIncomeCents
import com.companyb.companyapp.ui.screen.moneyToCents
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
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
         "sessionStatus":"PENDING","basePrice":"2500.00","finalPrice":"2500.00","remarks":null,
         "otherConcerns":null,"bookedAt":"2026-08-12T08:00:00+08:00","nextAppointmentDate":null,
         "version":1,"isVoided":false,"practitioners":[],"concerns":[]}],
       "commission":{"amount":"200.0000","productSalesCount":1}}"""

private const val EMPTY_DASHBOARD_JSON =
    """{"sessions":[],"commission":{"amount":"0.0000","productSalesCount":0}}"""

// #149 — PATCH responses for the inline-edit tests (SessionResponse shapes).
private const val PATCH_STATUS_RESPONSE_JSON =
    """{"id":"s1","clientId":"c1","branchDayId":"bd1","requestedPractitionerId":null,
        "sessionType":"SECOND_SESSION","isWalkIn":false,"sessionStatus":"COMPLETED",
        "basePrice":"2500.00","finalPrice":"2500.00","remarks":null,"otherConcerns":null,
        "bookedAt":null,"nextAppointmentDate":null,"version":2}"""

private const val PATCH_PRICE_JSON =
    """{"id":"s1","clientId":"c1","branchDayId":"bd1","requestedPractitionerId":null,
        "sessionType":"REGULAR","isWalkIn":false,"sessionStatus":"COMPLETED",
        "basePrice":"2500.00","finalPrice":"3000.00","remarks":null,"otherConcerns":null,
        "bookedAt":null,"nextAppointmentDate":null,"version":2}"""

private const val PATCH_STATUS_JSON =
    """{"id":"s1","clientId":"c1","branchDayId":"bd1","requestedPractitionerId":null,
        "sessionType":"REGULAR","isWalkIn":false,"sessionStatus":"COMPLETED",
        "basePrice":"2500.00","finalPrice":"2500.00","remarks":null,"otherConcerns":null,
        "bookedAt":null,"nextAppointmentDate":null,"version":2}"""

private const val DASHBOARD_JSON_V2 =
    """{"sessions":[
        {"id":"s1","clientId":"c1","clientName":"Test Client","sessionType":"REGULAR","isWalkIn":false,
         "sessionStatus":"PENDING","basePrice":"2500.00","finalPrice":"2500.00","remarks":null,
         "otherConcerns":null,"bookedAt":"2026-08-12T08:00:00+08:00","nextAppointmentDate":null,
         "version":2,"isVoided":false,"practitioners":[],"concerns":[]}],
       "commission":{"amount":"200.0000","productSalesCount":1}}"""

// #403 — the day-status read behind the REMITTED-day reason gate.
private const val DAY_TODAY_PATH = "/api/branches/b1/today"

private const val TODAY_REMITTED_JSON = """{"branchDayId":"bd1","status":"REMITTED"}"""

private const val TODAY_OPEN_JSON = """{"branchDayId":"bd1","status":"OPEN"}"""

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
        AppSessionState.clear()
        // #498 — dashboard tests run clocked-in (branch b1); day d1 is inert for the
        // branch-leg gates under test.
        AppSessionState.setClockedIn(
            "b1",
            "Branch A",
            ClockInResponse(
                id = "a1",
                branchDayId = "d1",
                userId = "u1",
                markedBy = "u1",
                clockIn = "2026-08-10T08:00:00+08:00",
                clockOut = null,
                isRelief = false,
            ),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        AppSessionState.clear()
    }

    // #156 — canEdit resolves branch-scoped vs the selected branch (setup sets "b1").
    private fun editRow(): List<UserCapabilityResponse> =
        listOf(
            UserCapabilityResponse(
                CapabilityCodes.EDIT_BRANCH_DATA,
                com.companyb.companyapp.contracts.authorization.CapabilityContextType.BRANCH,
                "b1",
                com.companyb.companyapp.contracts.authorization.CapabilitySourceType.MANUAL_OVERRIDE,
            ),
        )

    private fun coordinatorEditRow(): List<UserCapabilityResponse> =
        editRow() +
            UserCapabilityResponse(
                CapabilityCodes.EDIT_PAST_DAY,
                com.companyb.companyapp.contracts.authorization.CapabilityContextType.BRANCH,
                "b1",
                com.companyb.companyapp.contracts.authorization.CapabilitySourceType.MANUAL_OVERRIDE,
            )

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
                // #504 ended the Ktor Auth plugin's silent 401 re-send (explicit bearer, no
                // silent retry) — the handler now sees 1 hit per poll; the VM must swallow it.
                // #581 refreshes this stale 2-hit premise.
                assertEquals(1, hits)
                assertFalse(vm.dashboardState.value is UiState.Error, "401 must be swallowed (global auth path)")
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value)
                assertNull(vm.lastData.value)

                // pass-2: 401 pauses polling (the session is dead — symmetric with 403).
                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                assertEquals(1, hits, "401 must stop the poll loop")
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
                // hit1 = 403; hit 2 = the direct refresh (the sync Loading pre-set makes the
                // restarted loop's first iteration skip it — pass-2 double-launch fix);
                // hit 3 = the loop's next 30s iteration — polling is alive again.
                assertEquals(3, requestCount, "retry must restart the poll loop without double-fetching")
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
    fun transport_exceptions_count_against_stale_thresholds() =
        runTest(testScheduler) {
            var hits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler {
                            hits++
                            throw java.io.IOException("connection refused")
                        },
                    ),
                )
            try {
                // An exception path never reaches onNonSuccess (pass-1 HARD): the VM must
                // count it via the handler's onError hook, or a dead network would silently
                // freeze the last data with no stale banner and no escalation. Ktor's
                // HttpRequestRetry retries thrown exceptions (3x, virtual delays) — poll 1
                // exhausts its retries at ~+7s.
                runCurrent()
                advanceTimeBy(10_000.milliseconds)
                runCurrent()
                assertEquals(4, hits, "initial attempt + 3 retries per poll")
                assertIs<UiState.Error>(vm.dashboardState.value)
                assertEquals(DashboardPollStatus.FRESH, vm.pollStatus.value)

                // Poll 2 fires ~+37s (completion + 30s), exhausts retries by ~+44s.
                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                advanceTimeBy(10_000.milliseconds)
                runCurrent()
                assertEquals(8, hits)
                assertEquals(
                    DashboardPollStatus.STALE,
                    vm.pollStatus.value,
                    "exception-path failures must reach the stale thresholds",
                )
            } finally {
                vm.pause()
            }
        }

    @Test
    fun back_to_back_refresh_calls_launch_single_fetch() =
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
                // The synchronous Loading pre-set must hold from the caller's frame — two
                // back-to-back calls (e.g. retryAfterForbidden's refresh + the restarted
                // poll's first iteration) must not both dispatch.
                vm.refresh()
                vm.refresh()
                runCurrent()
                assertEquals(1, requestCount)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun refresh_without_selected_branch_fails_closed_without_request() =
        runTest(testScheduler) {
            AppSessionState.clear()
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

    // --- #149 inline editing (desktop only, #97 Q4 + ADR-0022) ---

    private fun editDashboardHandler(
        patchResponse: (path: String) -> Pair<HttpStatusCode, String>,
        getJson: () -> String = { DASHBOARD_JSON },
        todayJson: String? = null,
        todayStatus: () -> HttpStatusCode = { HttpStatusCode.OK },
    ): MockRequestHandler =
        {
            if (it.method == HttpMethod.Get) {
                if (it.url.encodedPath == DAY_TODAY_PATH) {
                    respond(body = todayJson ?: TODAY_OPEN_JSON, status = todayStatus())
                } else {
                    respondOk(getJson())
                }
            } else {
                val (status, body) = patchResponse(it.url.encodedPath)
                respond(body = body, status = status)
            }
        }

    private fun MockRequestHandleScope.respond(
        body: String,
        status: HttpStatusCode,
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun can_edit_reflects_branch_capability_slice() {
        AppSessionState.setCapabilities(editRow())
        val granted =
            SessionDashboardViewModel(
                mockApiClient(
                    dashboardHandler { null },
                ),
            )
        try {
            assertTrue(granted.canEdit.value)
        } finally {
            granted.pause()
        }

        AppSessionState.setCapabilities(emptyList<UserCapabilityResponse>())
        val revoked =
            SessionDashboardViewModel(
                mockApiClient(
                    dashboardHandler { null },
                ),
            )
        try {
            assertFalse(revoked.canEdit.value)
        } finally {
            revoked.pause()
        }
    }

    @Test
    fun can_edit_tracks_capability_state_changes() =
        runTest(testScheduler) {
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler { null },
                    ),
                )
            try {
                runCurrent()
                assertFalse(vm.canEdit.value)

                AppSessionState.setCapabilities(editRow())
                runCurrent()

                assertTrue(vm.canEdit.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun capability_revocation_discards_open_editor() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler { null },
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("PENDING")
                assertNotNull(vm.editState.value)

                AppSessionState.setCapabilities(emptyList())
                runCurrent()

                assertNull(vm.editState.value)
                assertFalse(vm.canEdit.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun late_edit_response_cannot_overwrite_editor_started_after_revocation() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            val vm =
                SessionDashboardViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get) {
                            if (request.url.encodedPath == DAY_TODAY_PATH) {
                                respond(body = TODAY_OPEN_JSON, status = HttpStatusCode.OK)
                            } else {
                                respondOk(DASHBOARD_JSON)
                            }
                        } else {
                            requestStarted.complete(Unit)
                            releaseResponse.await()
                            respond(body = PATCH_STATUS_RESPONSE_JSON, status = HttpStatusCode.OK)
                        }
                    },
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                assertTrue(requestStarted.isCompleted)

                AppSessionState.setCapabilities(emptyList())
                runCurrent()
                assertNull(vm.editState.value)

                AppSessionState.setCapabilities(editRow())
                runCurrent()
                vm.startEdit("s1", DashboardEditField.FINAL_PRICE)
                assertEquals(DashboardEditField.FINAL_PRICE, vm.editState.value?.field)

                releaseResponse.complete(Unit)
                runCurrent()

                assertEquals(DashboardEditField.FINAL_PRICE, vm.editState.value?.field)
                assertFalse(vm.editState.value?.inFlight == true)
            } finally {
                releaseResponse.complete(Unit)
                vm.pause()
            }
        }

    @Test
    fun coordinator_revocation_discards_open_correction_editor() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(coordinatorEditRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON },
                            getJson = { DASHBOARD_JSON.replace("PENDING", "NO_SHOW") },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("CANCELLED")
                assertNotNull(vm.editState.value)

                AppSessionState.setCapabilities(editRow())
                runCurrent()

                assertNull(vm.editState.value)
                assertTrue(vm.canEdit.value)
                assertFalse(vm.canCorrectStatus.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun dashboard_poll_refreshes_day_status() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var todayJson = TODAY_OPEN_JSON
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        dashboardHandler(
                            todayJson = { todayJson },
                            onHit = { null },
                        ),
                    ),
                )
            try {
                runCurrent()
                assertEquals(DayStatus.OPEN, vm.dayStatus.value)

                todayJson = TODAY_REMITTED_JSON
                advanceTimeBy(30_000.milliseconds)
                runCurrent()

                assertEquals(DayStatus.REMITTED, vm.dayStatus.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun view_only_dashboard_does_not_poll_capability_gated_day_status() =
        runTest(testScheduler) {
            var dayHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient { request ->
                        if (request.url.encodedPath == DAY_TODAY_PATH) {
                            dayHits++
                        }
                        respondOk(DASHBOARD_JSON)
                    },
                )
            try {
                runCurrent()
                assertEquals(0, dayHits)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun failed_day_status_read_disables_editing_until_refresh() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON },
                            todayStatus = { HttpStatusCode.BadRequest },
                        ),
                    ),
                )
            try {
                runCurrent()
                assertNull(vm.dayStatus.value)

                vm.startEdit("s1", DashboardEditField.STATUS)
                assertNull(vm.editState.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun failed_day_status_refresh_keeps_active_draft() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var todayStatus = HttpStatusCode.OK
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON },
                            todayStatus = { todayStatus },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                todayStatus = HttpStatusCode.BadRequest

                vm.refresh()
                runCurrent()

                assertEquals("COMPLETED", vm.editState.value?.draft)
                assertFalse(vm.editState.value?.inFlight == true)
                assertNull(vm.dayStatus.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_final_price_success_recomputes_gross() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.OK to PATCH_PRICE_JSON },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.FINAL_PRICE)
                vm.updateDraft("3000.00")
                vm.commitEdit()
                runCurrent()

                assertEquals(300_000L, grossIncomeCents(vm.lastData.value!!.sessions))
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_unchanged_draft_exits_without_request() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                runCurrent()
                vm.commitEdit()
                runCurrent()

                assertEquals(0, patchHits, "an unchanged draft must not dispatch")
                assertNull(vm.editState.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun mid_edit_poll_keeps_draft_and_baseline() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("PENDING")

                advanceTimeBy(30_000.milliseconds)
                runCurrent()

                val state = vm.editState.value
                assertNotNull(state, "a poll must not close an open editor")
                assertEquals("PENDING", state.draft, "the draft owns the cell (Q4)")
                assertEquals(1, state.baselineVersion, "the version snapshot survives polls too")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun concurrent_status_change_surfaces_conflict_instead_of_client_invalid_status() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var json = DASHBOARD_JSON
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.Conflict to "{\"error\":\"version mismatch\"}"
                            },
                            getJson = { json },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")

                json = DASHBOARD_JSON_V2.replace("\"sessionStatus\":\"PENDING\"", "\"sessionStatus\":\"NO_SHOW\"")
                advanceTimeBy(30_000.milliseconds)
                runCurrent()

                vm.commitEdit()
                runCurrent()

                assertEquals(1, patchHits, "the edit-start baseline keeps COMPLETED dispatchable")
                assertTrue(vm.editState.value!!.conflict)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_403_clears_edit_silently_and_hides_affordance() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.Forbidden to """{"error":"forbidden"}"""
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                assertTrue(vm.canEdit.value)
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                assertEquals(1, patchHits)
                assertNull(vm.editState.value, "403 must exit the edit silently")
                assertFalse(vm.canEdit.value, "the affordance must vanish (Q4)")
                assertFalse(vm.isForbidden.value, "an edit 403 is capability revocation, not the attendance gate")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun edit_403_remains_revoked_after_pause_and_resume() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(coordinatorEditRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.Forbidden to "{\"error\":\"forbidden\"}" },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                vm.pause()
                vm.resume()
                runCurrent()

                assertFalse(vm.canEdit.value)
                assertFalse(vm.canCorrectStatus.value)
                assertNull(vm.editState.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun correction_403_revokes_all_status_edit_authority_fail_closed() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(coordinatorEditRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.Forbidden to "{\"error\":\"forbidden\"}" },
                            getJson = { DASHBOARD_JSON.replace("PENDING", "NO_SHOW") },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("PENDING")
                vm.commitEdit()
                runCurrent()

                assertFalse(vm.canEdit.value)
                assertFalse(vm.canCorrectStatus.value)
                assertNull(vm.editState.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_409_keeps_draft_and_reload_rebaselines_with_changed_mark() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            var json = DASHBOARD_JSON
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.Conflict to """{"error":"version mismatch"}"""
                            },
                            getJson = { json },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                val conflicted = vm.editState.value
                assertNotNull(conflicted)
                assertTrue(conflicted.conflict)
                assertNotNull(conflicted.error)
                assertEquals("COMPLETED", conflicted.draft, "the draft stays in the input (ADR-0022)")

                // The reload lands a v2 row whose status differs from the attempted draft.
                json = DASHBOARD_JSON_V2
                vm.reloadAfterConflict()
                runCurrent()

                val reloaded = vm.editState.value
                assertNotNull(reloaded)
                assertEquals(2, reloaded.baselineVersion, "the retry commits against the fresh version")
                assertFalse(reloaded.conflict)
                assertNull(reloaded.error)
                assertTrue(reloaded.fieldChangedRemotely, "fresh PENDING != attempted COMPLETED")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_transport_failure_keeps_draft_model_a() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                throw java.io.IOException("connection refused")
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                // Ktor's HttpRequestRetry retries thrown exceptions (3x, virtual delays);
                // onError fires once the retries exhaust (~+7s), never onNonSuccess (the
                // #147 exception-path lesson).
                advanceTimeBy(10_000.milliseconds)
                runCurrent()

                assertEquals(4, patchHits, "initial attempt + 3 retries")
                val state = vm.editState.value
                assertNotNull(state, "Model A: stay in edit mode")
                assertEquals("COMPLETED", state.draft)
                assertNotNull(state.error)
                assertFalse(state.inFlight)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun commit_invalid_price_fails_client_side_without_request() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_PRICE_JSON
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.FINAL_PRICE)
                vm.updateDraft("abc")
                vm.commitEdit()
                runCurrent()

                assertEquals(0, patchHits, "an invalid price must never reach the wire")
                val state = vm.editState.value
                assertNotNull(state)
                assertNotNull(state.error)
                assertFalse(state.inFlight)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun in_flight_double_commit_dispatches_one_request() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                // The synchronous inFlight pre-set must hold from the caller's frame
                // (the #135 double-tap pattern — the second commit sees inFlight).
                vm.commitEdit()
                vm.commitEdit()
                runCurrent()

                assertEquals(1, patchHits)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun conflict_state_blocks_commit_until_reload() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            var json = DASHBOARD_JSON
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.Conflict to """{"error":"version mismatch"}"""
                            },
                            getJson = { json },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                assertEquals(1, patchHits)

                // Pass-1 finding: a blur-commit on the Reload click must NOT re-dispatch the
                // doomed stale-version PATCH (it swallowed the first Reload click).
                vm.commitEdit()
                runCurrent()
                assertEquals(1, patchHits, "conflict-state commits are blocked (Reload is the sanctioned path)")

                json = DASHBOARD_JSON_V2
                vm.reloadAfterConflict()
                runCurrent()
                assertEquals(2, vm.editState.value!!.baselineVersion)
                assertFalse(vm.editState.value!!.conflict)

                // Retry against the fresh baseline now dispatches.
                vm.commitEdit()
                runCurrent()
                assertEquals(2, patchHits, "the retry commits after the reload")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun reload_after_failed_refresh_keeps_conflict() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var getHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.Conflict to """{"error":"version mismatch"}""" },
                            getJson = {
                                getHits++
                                if (getHits == 1) DASHBOARD_JSON else throw java.io.IOException("connection refused")
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                assertTrue(vm.editState.value!!.conflict)

                // The reload's refresh fails (transport, retried 4x) — the conflict must
                // stand: a false re-baseline would leave the user retrying a stale version
                // forever with no visible failure (pass-1 finding).
                vm.reloadAfterConflict()
                runCurrent()
                advanceTimeBy(10_000.milliseconds)
                runCurrent()

                val state = vm.editState.value
                assertNotNull(state)
                assertTrue(state.conflict, "a failed reload must not clear the conflict")
                assertNotNull(state.error)
                assertEquals(1, state.baselineVersion)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun start_edit_blocked_while_failure_error_shows() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { throw java.io.IOException("connection refused") },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                advanceTimeBy(10_000.milliseconds)
                runCurrent()
                assertNotNull(vm.editState.value!!.error)

                // A cell switch must not silently drop an attempted draft (the #142
                // field-switch draft-drop class) — the failed editor stays until discarded.
                vm.startEdit("s1", DashboardEditField.STATUS)
                assertEquals(DashboardEditField.STATUS, vm.editState.value!!.field)
                assertEquals("COMPLETED", vm.editState.value!!.draft)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun draft_edit_during_conflict_keeps_reload_path_visible() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var json = DASHBOARD_JSON
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { HttpStatusCode.Conflict to """{"error":"version mismatch"}""" },
                            getJson = { json },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                assertTrue(vm.editState.value!!.conflict)

                // Pass-2 finding: typing (or re-selecting) during a conflict must not clear
                // the error — the Reload action keys off it, and the commit stays blocked.
                vm.updateDraft("NO_SHOW")
                val state = vm.editState.value!!
                assertTrue(state.conflict)
                assertNotNull(state.error, "the conflict error + Reload must stay visible")

                // The Reload path still resolves the conflict with the new draft.
                json = DASHBOARD_JSON_V2
                vm.reloadAfterConflict()
                runCurrent()
                val reloaded = vm.editState.value!!
                assertFalse(reloaded.conflict)
                assertEquals("NO_SHOW", reloaded.draft)
                assertTrue(reloaded.fieldChangedRemotely, "fresh PENDING != attempted NO_SHOW")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun start_edit_clears_parked_machine_when_row_vanished() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var json = DASHBOARD_JSON
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { throw java.io.IOException("connection refused") },
                            getJson = { json },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()
                advanceTimeBy(10_000.milliseconds)
                runCurrent()
                assertNotNull(vm.editState.value!!.error)

                // The day rollover: the edited row drops out of today's list (membership is
                // incoming-authoritative). The editor is already invisible — a parked machine
                // with an error would wedge every future edit (pass-3 finding).
                json = EMPTY_DASHBOARD_JSON
                advanceTimeBy(30_000.milliseconds)
                runCurrent()

                vm.startEdit("s1", DashboardEditField.STATUS)
                assertNull(vm.editState.value, "the parked machine must not wedge new edits")

                // The real unlock: a NEW row's cell opens a fresh machine.
                json =
                    """{"sessions":[
                        {"id":"s2","clientId":"c2","clientName":"Client Two","sessionType":"REGULAR","isWalkIn":false,
                         "sessionStatus":"PENDING","basePrice":"2500.00","finalPrice":"2500.00","remarks":null,
                         "otherConcerns":null,"bookedAt":null,"nextAppointmentDate":null,
                         "version":1,"isVoided":false,"practitioners":[],"concerns":[]}],
                       "commission":{"amount":"0.0000","productSalesCount":0}}"""
                advanceTimeBy(30_000.milliseconds)
                runCurrent()
                vm.startEdit("s2", DashboardEditField.STATUS)
                assertEquals("s2", vm.editState.value!!.sessionId, "a present row's cell opens normally")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun discard_clears_machine_without_request() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("SECOND_SESSION")
                vm.discardEdit()
                runCurrent()

                assertEquals(0, patchHits)
                assertNull(vm.editState.value)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun status_edit_hits_the_status_endpoint() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchPath: String? = null
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = { path ->
                                patchPath = path
                                HttpStatusCode.OK to PATCH_STATUS_JSON
                            },
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                assertEquals("/api/sessions/s1/status", patchPath)
                assertEquals(
                    "COMPLETED",
                    vm.lastData.value!!
                        .sessions
                        .single()
                        .sessionStatus.name,
                )
            } finally {
                vm.pause()
            }
        }

    // --- #403 — REMITTED-day reason gating ---

    @Test
    fun reason_required_predicate_keys_off_remitted_only() {
        assertTrue(remittedReasonRequired(DayStatus.REMITTED))
        assertFalse(remittedReasonRequired(DayStatus.OPEN))
        assertFalse(remittedReasonRequired(DayStatus.PAST))
        assertFalse(remittedReasonRequired(null), "unknown day state degrades to optional — the server 400 guards")
    }

    @Test
    fun remitted_day_blocks_blank_reason_before_dispatch() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(coordinatorEditRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_PRICE_JSON
                            },
                            todayJson = TODAY_REMITTED_JSON,
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.FINAL_PRICE)
                runCurrent()
                assertEquals(DayStatus.REMITTED, vm.dayStatus.value)

                vm.updateDraft("3000.00")
                vm.commitEdit()

                assertEquals(0, patchHits, "a blank reason must never reach the wire")
                val state = vm.editState.value
                assertNotNull(state)
                assertFalse(state.inFlight)
                assertNotNull(state.error)
            } finally {
                vm.pause()
            }
        }

    @Test
    fun remitted_day_commit_with_reason_dispatches_and_clears() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(coordinatorEditRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_PRICE_JSON
                            },
                            todayJson = TODAY_REMITTED_JSON,
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.FINAL_PRICE)
                vm.updateDraft("3000.00")
                vm.updateReason("price correction after remit")
                vm.commitEdit()
                runCurrent()

                assertEquals(1, patchHits)
                assertNull(vm.editState.value, "a successful reasoned commit exits the editor")
            } finally {
                vm.pause()
            }
        }

    @Test
    fun open_day_commits_without_reason() =
        runTest(testScheduler) {
            AppSessionState.setCapabilities(editRow())
            var patchHits = 0
            val vm =
                SessionDashboardViewModel(
                    mockApiClient(
                        editDashboardHandler(
                            patchResponse = {
                                patchHits++
                                HttpStatusCode.OK to PATCH_STATUS_RESPONSE_JSON
                            },
                            todayJson = TODAY_OPEN_JSON,
                        ),
                    ),
                )
            try {
                runCurrent()
                vm.startEdit("s1", DashboardEditField.STATUS)
                runCurrent()
                assertEquals(DayStatus.OPEN, vm.dayStatus.value)

                vm.updateDraft("COMPLETED")
                vm.commitEdit()
                runCurrent()

                assertEquals(1, patchHits, "non-remitted days behave exactly as before")
                assertNull(vm.editState.value)
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
            sessionType = com.companyb.companyapp.contracts.session.SessionType.REGULAR,
            isWalkIn = false,
            sessionStatus =
                com.companyb.companyapp.contracts.session.SessionStatus
                    .valueOf(status),
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
        todayJson: () -> String = { TODAY_OPEN_JSON },
        onHit: MockRequestHandleScope.() -> HttpResponseData? = { null },
    ): MockRequestHandler =
        { request ->
            if (request.url.encodedPath == DAY_TODAY_PATH) {
                respondOk(todayJson())
            } else {
                if (responseDelayMs > 0) {
                    withContext(dispatcher) { delay(responseDelayMs) }
                }
                // The test block's response wins (error/forbidden cases); null falls back to OK.
                onHit() ?: respondOk(DASHBOARD_JSON)
            }
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
