package com.companyb.companyapp.remittance

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceDraftRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the #120 Remittance build — the locked #103 D1–D10: list (status-filtered), detail
 * with snapshot, pickers, and the ADR-0022 mutation axes (403 silent exit, 409 → reload +
 * changed-elsewhere notice) on every mutation, plus undo + header PATCH version locking.
 * Uses the #93 handler-based MockEngine + `runTest(testScheduler)` virtual-time drain pattern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemittanceViewModelTest {
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

    @Test
    fun loadRemittances_success_emits_list_with_branch_and_status_params() =
        runTest(testScheduler) {
            val recorded = mutableListOf<Pair<String, String>>()
            val vm =
                RemittanceViewModel(
                    mockApiClient(
                        remittanceHandler { request ->
                            recorded.add(
                                request.url.parameters["branchId"].orEmpty() to
                                    request.url.parameters["status"].orEmpty(),
                            )
                        },
                    ),
                )

            vm.loadRemittances("b1", "DRAFT")
            runCurrent()

            val state = assertIs<UiState.Success<List<RemittanceResponse>>>(vm.remittanceList.value)
            assertEquals(expected = listOf("r1"), actual = state.data.map { it.id })
            assertEquals(expected = listOf("b1" to "DRAFT"), actual = recorded)
        }

    @Test
    fun loadRemittances_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                RemittanceViewModel(
                    mockApiClient(
                        remittanceHandler(listStatus = HttpStatusCode.BadRequest),
                    ),
                )

            vm.loadRemittances("b1", "ALL")
            runCurrent()

            assertIs<UiState.Error>(vm.remittanceList.value)
        }

    @Test
    fun loadRemittances_double_call_while_same_key_in_flight_skips_duplicate() =
        runTest(testScheduler) {
            var listGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient(
                        remittanceHandler(onListRequest = { listGets++ }),
                    ),
                )

            vm.loadRemittances("b1", "DRAFT")
            vm.loadRemittances("b1", "DRAFT")
            runCurrent()

            // The entry effect + the tab effect both fire the default tab's load on first
            // composition; the synchronous per-key guard coalesces them (the #143 in-flight
            // no-refire shape).
            assertEquals(expected = 1, actual = listGets)
        }

    @Test
    fun loadRemittances_other_tab_not_skipped_while_one_in_flight() =
        runTest(testScheduler) {
            var listGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient(
                        remittanceHandler(onListRequest = { listGets++ }),
                    ),
                )

            vm.loadRemittances("b1", "DRAFT")
            vm.loadRemittances("b1", "SUBMITTED")
            runCurrent()

            // Per-key guard: a tab switch during another tab's load must still fetch the new tab
            // (a single shared slot would skip it and leave the tab unloaded).
            assertEquals(expected = 2, actual = listGets)
        }

    @Test
    fun loadRemittances_success_writes_tab_mirror() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.loadRemittances("b1", "DRAFT")
            runCurrent()

            val held = vm.lastByTab.value["b1:DRAFT"]
            assertNotNull(held)
            assertEquals(expected = listOf("r1"), actual = held.map { it.id })
        }

    @Test
    fun loadRemittances_failure_keeps_last_list_for_tab() =
        runTest(testScheduler) {
            var listStatus = HttpStatusCode.OK
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/remittances"
                        ) {
                            jsonRespond(status = listStatus, body = LIST_JSON)
                        } else {
                            error("unexpected request: ${request.method} ${request.url.encodedPath}")
                        }
                    },
                )

            vm.loadRemittances("b1", "DRAFT")
            runCurrent()
            listStatus = HttpStatusCode.BadRequest
            vm.loadRemittances("b1", "DRAFT")
            runCurrent()

            // The mirror keeps the last successful list through an Error so the render gate never
            // swaps held rows for an ErrorCard (keep-last, #161 port).
            assertIs<UiState.Error>(vm.remittanceList.value)
            assertEquals(expected = listOf("r1"), actual = vm.lastByTab.value["b1:DRAFT"]?.map { it.id })
        }

    @Test
    fun loadRemittances_per_branch_mirror_isolated() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.loadRemittances("b1", "DRAFT")
            runCurrent()
            vm.loadRemittances("b2", "DRAFT")
            runCurrent()

            // One VM serves multiple branches (the list route takes no branch arg); the mirrors
            // must not clobber each other.
            assertEquals(expected = listOf("r1"), actual = vm.lastByTab.value["b1:DRAFT"]?.map { it.id })
            assertEquals(expected = listOf("r1"), actual = vm.lastByTab.value["b2:DRAFT"]?.map { it.id })
        }

    @Test
    fun loadRemittances_failure_clears_guard_so_retry_fires() =
        runTest(testScheduler) {
            var listGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient(
                        remittanceHandler(
                            listStatus = HttpStatusCode.BadRequest,
                            onListRequest = { listGets++ },
                        ),
                    ),
                )

            vm.loadRemittances("b1", "DRAFT")
            runCurrent()
            assertIs<UiState.Error>(vm.remittanceList.value)

            // The guard clears on the failure path — a retry must not silently no-op (#140
            // stuck-Loading class; the AuditLog every-failure-path discipline).
            vm.loadRemittances("b1", "DRAFT")
            runCurrent()
            assertEquals(expected = 2, actual = listGets)
        }

    @Test
    fun createDraft_success_emits_draft() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.createDraft(
                CreateRemittanceDraftRequest(
                    id = "new-id",
                    type = com.companyb.companyapp.contracts.remittance.RemittanceType.SESSION,
                    branchId = "b1",
                    method = com.companyb.companyapp.contracts.remittance.RemittanceMethod.BANK_TRANSFER,
                    dateRangeStart = "2026-08-01",
                    dateRangeEnd = "2026-08-09",
                ),
            )
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceResponse>>(vm.createDraftResult.value)
            assertEquals(expected = "new-id", actual = state.data.id)
        }

    @Test
    fun loadRemittance_success_emits_detail_with_lines_and_snapshot() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.loadRemittance("r1")
            runCurrent()

            val state =
                assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)
            assertEquals(expected = "r1", actual = state.data.id)
            assertEquals(expected = 3, actual = state.data.version)
            assertEquals(expected = 1, actual = state.data.lines.size)
            assertEquals(expected = "1200.00", actual = state.data.snapshot?.netIncome)
        }

    @Test
    fun loadRemittance_resetNotice_false_preserves_notice_default_resets() =
        runTest(testScheduler) {
            val vm =
                RemittanceViewModel(
                    mockApiClient(remittanceHandler(updateStatus = HttpStatusCode.Conflict)),
                )
            vm.loadRemittance("r1")
            runCurrent()
            vm.updateHeader("r1", headerRequest(version = 3))
            runCurrent()
            assertTrue(vm.detailChangedNotice.value)

            vm.loadRemittance("r1", resetNotice = false)
            runCurrent()
            assertTrue(vm.detailChangedNotice.value)

            vm.loadRemittance("r1")
            runCurrent()
            assertEquals(expected = false, actual = vm.detailChangedNotice.value)
        }

    @Test
    fun loadRemittance_staleSuccessLanding_dropsStaleBody() =
        runTest(testScheduler) {
            // #484 — latest-wins: gate the first load, land a newer load first, then release
            // the stale one with a divergent version. Without the guard the stale body would
            // last-writer-win over the newer version/snapshot/Undo state.
            val staleGate = CompletableDeferred<Unit>()
            var detailGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/remittances/r1"
                        ) {
                            detailGets++
                            if (detailGets == 1) {
                                staleGate.await()
                                jsonRespond(status = HttpStatusCode.OK, body = STALE_DETAIL_JSON)
                            } else {
                                jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                            }
                        } else {
                            error("unexpected request: ${request.method} ${request.url.encodedPath}")
                        }
                    },
                )

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadRemittance("r1")
            advanceUntilIdle()

            staleGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(expected = 2, actual = detailGets)
            val state =
                assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)
            assertEquals(expected = 3, actual = state.data.version)
        }

    @Test
    fun loadRemittance_staleFailure_doesNotClobberNewerSuccess() =
        runTest(testScheduler) {
            // #484 (#176 leg): a superseded failure must write no Error onto the moved-on
            // surface — the newer Success stands.
            val staleGate = CompletableDeferred<Unit>()
            var detailGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/remittances/r1"
                        ) {
                            detailGets++
                            if (detailGets == 1) {
                                staleGate.await()
                                // 400, not 500: the client's HttpRequestRetry re-issues 5xx
                                // inside the same landing (a retry is not a newer load).
                                jsonRespond(status = HttpStatusCode.BadRequest, body = "{}")
                            } else {
                                jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                            }
                        } else {
                            error("unexpected request: ${request.method} ${request.url.encodedPath}")
                        }
                    },
                )

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadRemittance("r1")
            advanceUntilIdle()
            assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)

            staleGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(expected = 2, actual = detailGets)
            val state =
                assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)
            assertEquals(expected = 3, actual = state.data.version)
        }

    @Test
    fun reloadDetail_resetsNotice_and_refetches() =
        runTest(testScheduler) {
            // #484 — the central success-path funnel carries loadRemittance defaults: a clean
            // post-mutation reload resets the changed-elsewhere notice and re-fetches.
            var detailGets = 0
            val base = remittanceHandler(updateStatus = HttpStatusCode.Conflict)
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/remittances/r1"
                        ) {
                            detailGets++
                        }
                        base(request)
                    },
                )
            vm.loadRemittance("r1")
            runCurrent()
            vm.updateHeader("r1", headerRequest(version = 3))
            runCurrent()
            assertTrue(vm.detailChangedNotice.value)
            assertEquals(expected = 2, actual = detailGets)

            vm.reloadDetail("r1")
            runCurrent()

            assertEquals(expected = 3, actual = detailGets)
            assertEquals(expected = false, actual = vm.detailChangedNotice.value)
            assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)
        }

    @Test
    fun picker_loads_success_emit_entries() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.loadSessionPicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()
            val sessions =
                assertIs<UiState.Success<List<RemittanceSessionPickerEntryResponse>>>(vm.sessionPicker.value)
            assertEquals(expected = "s1", actual = sessions.data.first().id)

            vm.loadProductSalePicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()
            val products =
                assertIs<UiState.Success<List<RemittanceProductSalePickerEntryResponse>>>(vm.productSalePicker.value)
            assertEquals(expected = "ps1", actual = products.data.first().id)

            vm.loadDayPicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()
            val days =
                assertIs<UiState.Success<List<RemittanceDayPickerEntryResponse>>>(vm.dayPicker.value)
            assertEquals(expected = "d1", actual = days.data.first().id)
        }

    @Test
    fun picker_loaded_range_tracks_requested_range_per_load() =
        runTest(testScheduler) {
            // #490 — marker commits are range-keyed against the committed detail, so the test
            // follows the production flow: detail first, then pickers for its range; a header
            // range edit recommits the detail before the new-range reloads.
            var detailBody = DETAIL_JSON
            val base = remittanceHandler()
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath.startsWith("/api/remittances/")
                        ) {
                            jsonRespond(status = HttpStatusCode.OK, body = detailBody)
                        } else {
                            base(request)
                        }
                    },
                )

            assertEquals(expected = null, actual = vm.pickerLoadedRange.value)

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadSessionPicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()
            assertEquals(expected = "2026-08-01" to "2026-08-09", actual = vm.pickerLoadedRange.value)

            // A header range edit reloads for the new range (#483) — the key follows the load.
            detailBody = DETAIL_JSON_RANGE_B
            vm.loadRemittance("r1")
            runCurrent()
            vm.loadDayPicker("b1", "2026-08-10", "2026-08-20")
            runCurrent()
            assertEquals(expected = "2026-08-10" to "2026-08-20", actual = vm.pickerLoadedRange.value)
        }

    @Test
    fun picker_staleRangeLanding_commitsNeitherEntriesNorMarker() =
        runTest(testScheduler) {
            // #490 ordering (2): range change fires new-range loads; the old-range landing
            // arriving after the current-range Success must move neither entries nor marker —
            // otherwise the gate renders Loading with no reload to unwedge it.
            var detailBody = DETAIL_JSON
            val sessionGate = CompletableDeferred<Unit>()
            var sessionGets = 0
            val base = remittanceHandler()
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath.startsWith("/api/remittances/")
                        ) {
                            jsonRespond(status = HttpStatusCode.OK, body = detailBody)
                        } else if (request.method == HttpMethod.Get &&
                            request.url.encodedPath.endsWith("/remittance-sessions")
                        ) {
                            sessionGets++
                            if (sessionGets == 1) {
                                sessionGate.await()
                                jsonRespond(status = HttpStatusCode.OK, body = SESSIONS_JSON)
                            } else {
                                jsonRespond(status = HttpStatusCode.OK, body = SESSIONS_JSON_RANGE_B)
                            }
                        } else {
                            base(request)
                        }
                    },
                )

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadSessionPicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()

            detailBody = DETAIL_JSON_RANGE_B
            vm.loadRemittance("r1")
            runCurrent()
            vm.loadSessionPicker("b1", "2026-08-10", "2026-08-20")
            advanceUntilIdle()

            sessionGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(expected = 2, actual = sessionGets)
            assertEquals(
                expected = "2026-08-10" to "2026-08-20",
                actual = vm.pickerLoadedRange.value,
            )
            val sessions =
                assertIs<UiState.Success<List<RemittanceSessionPickerEntryResponse>>>(vm.sessionPicker.value)
            assertEquals(expected = listOf("s9"), actual = sessions.data.map { it.id })
        }

    @Test
    fun picker_staleRangeFailure_doesNotClobberNewerSuccess() =
        runTest(testScheduler) {
            // #490 finding (3): an old-range failure landing after the current-range Success
            // must write no Error — the Success stands with its Retry-free surface.
            var detailBody = DETAIL_JSON
            val dayGate = CompletableDeferred<Unit>()
            var dayGets = 0
            val base = remittanceHandler()
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath.startsWith("/api/remittances/")
                        ) {
                            jsonRespond(status = HttpStatusCode.OK, body = detailBody)
                        } else if (request.method == HttpMethod.Get &&
                            request.url.encodedPath.endsWith("/remittance-days")
                        ) {
                            dayGets++
                            if (dayGets == 1) {
                                dayGate.await()
                                jsonRespond(status = HttpStatusCode.BadRequest, body = "{}")
                            } else {
                                jsonRespond(status = HttpStatusCode.OK, body = DAYS_JSON)
                            }
                        } else {
                            base(request)
                        }
                    },
                )

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadDayPicker("b1", "2026-08-01", "2026-08-09")
            runCurrent()

            detailBody = DETAIL_JSON_RANGE_B
            vm.loadRemittance("r1")
            runCurrent()
            vm.loadDayPicker("b1", "2026-08-10", "2026-08-20")
            advanceUntilIdle()
            assertIs<UiState.Success<List<RemittanceDayPickerEntryResponse>>>(vm.dayPicker.value)

            dayGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(expected = 2, actual = dayGets)
            assertIs<UiState.Success<List<RemittanceDayPickerEntryResponse>>>(
                vm.dayPicker.value,
                "a stale-range failure must not clobber the current-range Success",
            )
            assertEquals(
                expected = "2026-08-10" to "2026-08-20",
                actual = vm.pickerLoadedRange.value,
            )
        }

    @Test
    fun loadRemittance_staleFirstLanding_holdsLoadingUntilNewerLands() =
        runTest(testScheduler) {
            // #490 — double-initial overlap whose stale landing arrives FIRST: no committed
            // detail exists, so the fallback throws and the stale leg holds Loading (logged,
            // never rendered) until the superseding load commits.
            val releaseFirst = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()
            var detailGets = 0
            val vm =
                RemittanceViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/remittances/r1"
                        ) {
                            detailGets++
                            if (detailGets == 1) {
                                releaseFirst.await()
                            } else {
                                releaseSecond.await()
                            }
                            jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                        } else {
                            error("unexpected request: ${request.method} ${request.url.encodedPath}")
                        }
                    },
                )

            vm.loadRemittance("r1")
            runCurrent()
            vm.loadRemittance("r1")
            advanceUntilIdle()
            assertEquals(expected = 2, actual = detailGets)

            releaseFirst.complete(Unit)
            advanceUntilIdle()

            assertIs<UiState.Loading>(
                vm.remittanceDetail.value,
                "the stale-first landing must hold Loading, not Error",
            )

            releaseSecond.complete(Unit)
            advanceUntilIdle()

            val state =
                assertIs<UiState.Success<RemittanceDetailResponse>>(vm.remittanceDetail.value)
            assertEquals(expected = 3, actual = state.data.version)
        }

    @Test
    fun addLine_success_emits_line() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.addLine(
                "r1",
                CreateRemittanceLineRequest(
                    id = "l1",
                    type = com.companyb.companyapp.contracts.remittance.RemittanceLineType.SESSION,
                    sessionId = "s1",
                    amount = "500.00",
                ),
            )
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceLineResponse>>(vm.lineResult.value)
            assertEquals(expected = "l1", actual = state.data.id)
        }

    @Test
    fun addLine_403_silent_exit_leaves_state_idle() =
        runTest(testScheduler) {
            val vm =
                RemittanceViewModel(
                    mockApiClient(remittanceHandler(lineStatus = HttpStatusCode.Forbidden)),
                )

            vm.addLine(
                "r1",
                CreateRemittanceLineRequest(
                    id = "l1",
                    type = com.companyb.companyapp.contracts.remittance.RemittanceLineType.SESSION,
                    sessionId = "s1",
                    amount = "500.00",
                ),
            )
            runCurrent()

            assertIs<UiState.Idle>(vm.lineResult.value)
        }

    @Test
    fun addLine_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(lineStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.addLine(
                "r1",
                CreateRemittanceLineRequest(
                    id = "l1",
                    type = com.companyb.companyapp.contracts.remittance.RemittanceLineType.SESSION,
                    sessionId = "s1",
                    amount = "500.00",
                ),
            )
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.lineResult.value)
        }

    @Test
    fun deleteLine_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(deleteLineStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.deleteLine("r1", "l1")
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.deleteLineResult.value)
        }

    @Test
    fun addDayBreakdown_success_emits_breakdown() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.addDayBreakdown("r1", AddDayBreakdownRequest(id = "bd1", branchDayId = "d1"))
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceDayBreakdownResponse>>(vm.dayBreakdownResult.value)
            assertEquals(expected = "bd1", actual = state.data.id)
        }

    @Test
    fun addDayBreakdown_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            // #484 — the only adapter whose terminal path had no pin: it must route through
            // the shared 403/409 handler like the other six.
            val handler = remittanceHandler(dayStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.addDayBreakdown("r1", AddDayBreakdownRequest(id = "bd1", branchDayId = "d1"))
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.dayBreakdownResult.value)
        }

    @Test
    fun deleteDayBreakdown_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(deleteDayStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.deleteDayBreakdown("r1", "bd1")
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.dayBreakdownDeleteResult.value)
        }

    @Test
    fun submit_success_emits_submit_response() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.submit("r1", SubmitRemittanceRequest(expectedVersion = 3))
            runCurrent()

            val state =
                assertIs<UiState.Success<RemittanceSubmitResponse>>(vm.submitResult.value)
            assertEquals(expected = "r1", actual = state.data.id)
            assertEquals(expected = "1234.56", actual = state.data.netIncome)
        }

    @Test
    fun submit_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(submitStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.submit("r1", SubmitRemittanceRequest(expectedVersion = 3))
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.submitResult.value)
        }

    @Test
    fun submit_403_silent_exit_leaves_state_idle() =
        runTest(testScheduler) {
            val vm =
                RemittanceViewModel(
                    mockApiClient(remittanceHandler(submitStatus = HttpStatusCode.Forbidden)),
                )

            vm.submit("r1", SubmitRemittanceRequest(expectedVersion = 3))
            runCurrent()

            assertIs<UiState.Idle>(vm.submitResult.value)
        }

    @Test
    fun undo_success_emits_draft_response() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.undo("r1", UndoRemittanceRequest(expectedVersion = 4, reason = "wrong amounts"))
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceResponse>>(vm.undoResult.value)
            assertEquals(expected = "DRAFT", actual = state.data.status.name)
        }

    @Test
    fun undo_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(undoStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.undo("r1", UndoRemittanceRequest(expectedVersion = 4, reason = "wrong amounts"))
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.undoResult.value)
        }

    @Test
    fun updateHeader_success_emits_updated_response() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.updateHeader("r1", headerRequest(version = 3))
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceResponse>>(vm.headerUpdateResult.value)
            assertEquals(expected = "PRODUCT", actual = state.data.type.name)
        }

    @Test
    fun updateHeader_409_reloads_detail_and_sets_changed_notice() =
        runTest(testScheduler) {
            val handler = remittanceHandler(updateStatus = HttpStatusCode.Conflict)
            var detailGets = 0
            val wrapped: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances/r1"
                ) {
                    detailGets++
                }
                handler(request)
            }
            val vm = RemittanceViewModel(mockApiClient(wrapped))
            vm.loadRemittance("r1")
            runCurrent()

            vm.updateHeader("r1", headerRequest(version = 3))
            runCurrent()

            assertEquals(expected = 2, actual = detailGets)
            assertTrue(vm.detailChangedNotice.value)
            assertIs<UiState.Idle>(vm.headerUpdateResult.value)
        }

    @Test
    fun updateHeader_403_silent_exit_leaves_state_idle() =
        runTest(testScheduler) {
            val vm =
                RemittanceViewModel(
                    mockApiClient(remittanceHandler(updateStatus = HttpStatusCode.Forbidden)),
                )

            vm.updateHeader("r1", headerRequest(version = 3))
            runCurrent()

            assertIs<UiState.Idle>(vm.headerUpdateResult.value)
        }

    @Test
    fun loadDrift_success_emits_drift() =
        runTest(testScheduler) {
            val vm = RemittanceViewModel(mockApiClient(remittanceHandler()))

            vm.loadDrift("r1")
            runCurrent()

            val state = assertIs<UiState.Success<RemittanceDriftResponse>>(vm.drift.value)
            assertEquals(expected = "1350.00", actual = state.data.currentExpenses)
        }

    private fun headerRequest(version: Int) =
        UpdateRemittanceHeaderRequest(
            type = com.companyb.companyapp.contracts.remittance.RemittanceType.PRODUCT,
            method = com.companyb.companyapp.contracts.remittance.RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = "2026-08-01",
            dateRangeEnd = "2026-08-09",
            expectedVersion = version,
        )

    private fun remittanceHandler(
        listStatus: HttpStatusCode = HttpStatusCode.OK,
        lineStatus: HttpStatusCode = HttpStatusCode.OK,
        deleteLineStatus: HttpStatusCode = HttpStatusCode.OK,
        dayStatus: HttpStatusCode = HttpStatusCode.OK,
        deleteDayStatus: HttpStatusCode = HttpStatusCode.OK,
        submitStatus: HttpStatusCode = HttpStatusCode.OK,
        undoStatus: HttpStatusCode = HttpStatusCode.OK,
        updateStatus: HttpStatusCode = HttpStatusCode.OK,
        onListRequest: (HttpRequestData) -> Unit = {},
    ): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/remittances" -> {
                    onListRequest(request)
                    jsonRespond(status = listStatus, body = LIST_JSON)
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath == "/api/remittances" -> {
                    jsonRespond(status = HttpStatusCode.Created, body = CREATED_JSON)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/drift") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = DRIFT_JSON)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.startsWith("/api/remittances/") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = DETAIL_JSON)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/remittance-sessions") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = SESSIONS_JSON)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/remittance-product-sales") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = PRODUCT_SALES_JSON)
                }

                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("/remittance-days") -> {
                    jsonRespond(status = HttpStatusCode.OK, body = DAYS_JSON)
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.contains("/lines") -> {
                    jsonRespond(status = lineStatus, body = LINE_JSON)
                }

                request.method == HttpMethod.Delete &&
                    request.url.encodedPath.contains("/lines/") -> {
                    jsonRespond(status = deleteLineStatus, body = "")
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.contains("/day-breakdowns") -> {
                    jsonRespond(status = dayStatus, body = DAY_BREAKDOWN_JSON)
                }

                request.method == HttpMethod.Delete &&
                    request.url.encodedPath.contains("/day-breakdowns/") -> {
                    jsonRespond(status = deleteDayStatus, body = "")
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.contains("/submit") -> {
                    jsonRespond(status = submitStatus, body = SUBMIT_JSON)
                }

                request.method == HttpMethod.Post &&
                    request.url.encodedPath.contains("/undo") -> {
                    jsonRespond(status = undoStatus, body = UNDO_JSON)
                }

                request.method == HttpMethod.Patch &&
                    request.url.encodedPath.startsWith("/api/remittances/") -> {
                    jsonRespond(status = updateStatus, body = PATCHED_JSON)
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
        }

    private fun MockRequestHandleScope.jsonRespond(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val LIST_JSON =
            """[
                {"id":"r1","type":"SESSION","status":"DRAFT","branchId":"b1","method":"BANK_TRANSFER","submittedDate":"","submittedAt":null,"submittedBy":"u1","dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-09T01:00:00Z","version":3,"netIncome":null}
            ]"""

        const val CREATED_JSON =
            """{"id":"new-id","type":"SESSION","status":"DRAFT","branchId":"b1","method":"BANK_TRANSFER","submittedDate":"","submittedAt":null,"submittedBy":"u1","dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-09T01:00:00Z","version":1,"netIncome":null}"""

        const val STALE_DETAIL_JSON =
            """{
                "id":"r1","type":"SESSION","status":"SUBMITTED","branchId":"b1","method":"BANK_TRANSFER",
                "submittedDate":"2026-08-09","submittedAt":"2026-08-09T01:00:00Z","submittedBy":"u1",
                "dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-01T01:00:00Z",
                "version":99,
                "lines":[{"id":"l1","remittanceId":"r1","type":"SESSION","sessionId":"s1","productSaleId":null,"createdBy":"u1","createdAt":"2026-08-01T01:00:00Z","deletedBy":null,"deletedAt":null,"amount":"500.00"}],
                "totalAmount":"500.00",
                "dayBreakdowns":[{"id":"bd1","remittanceId":"r1","branchDayId":"d1"}],
                "snapshot":{"remittanceId":"r1","grossIncome":"1234.56","totalCompensation":"100.00","totalExpenses":"1200.00","netIncome":"1200.00","snapshottedAt":"2026-08-09T01:00:00Z"}
            }"""

        const val DETAIL_JSON =
            """{
                "id":"r1","type":"SESSION","status":"SUBMITTED","branchId":"b1","method":"BANK_TRANSFER",
                "submittedDate":"2026-08-09","submittedAt":"2026-08-09T01:00:00Z","submittedBy":"u1",
                "dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-01T01:00:00Z",
                "version":3,
                "lines":[{"id":"l1","remittanceId":"r1","type":"SESSION","sessionId":"s1","productSaleId":null,"createdBy":"u1","createdAt":"2026-08-01T01:00:00Z","deletedBy":null,"deletedAt":null,"amount":"500.00"}],
                "totalAmount":"500.00",
                "dayBreakdowns":[{"id":"bd1","remittanceId":"r1","branchDayId":"d1"}],
                "snapshot":{"remittanceId":"r1","grossIncome":"1234.56","totalCompensation":"100.00","totalExpenses":"1200.00","netIncome":"1200.00","snapshottedAt":"2026-08-09T01:00:00Z"}
            }"""

        const val SESSIONS_JSON =
            """[
                {"id":"s1","clientName":"John Doe","bookedAt":"2026-08-01T02:00:00Z","sessionStatus":"COMPLETED","finalPrice":"500.00"}
            ]"""

        const val SESSIONS_JSON_RANGE_B =
            """[
                {"id":"s9","clientName":"Jane Roe","bookedAt":"2026-08-10T02:00:00Z","sessionStatus":"COMPLETED","finalPrice":"700.00"}
            ]"""

        const val DETAIL_JSON_RANGE_B =
            """{
                "id":"r1","type":"SESSION","status":"DRAFT","branchId":"b1","method":"BANK_TRANSFER",
                "submittedDate":"","submittedAt":null,"submittedBy":"u1",
                "dateRangeStart":"2026-08-10","dateRangeEnd":"2026-08-20","createdAt":"2026-08-01T01:00:00Z",
                "version":4,
                "lines":[],
                "totalAmount":"0.00",
                "dayBreakdowns":[],
                "snapshot":null
            }"""

        const val PRODUCT_SALES_JSON =
            """[
                {"id":"ps1","productName":"Vitamin C","quantity":2,"totalAmountAtTime":"300.00","soldAt":"2026-08-01T02:00:00Z"}
            ]"""

        const val DAYS_JSON =
            """[
                {"id":"d1","date":"2026-08-09","status":"OPEN"}
            ]"""

        const val LINE_JSON =
            """{"id":"l1","remittanceId":"r1","type":"SESSION","sessionId":"s1","productSaleId":null,"createdBy":"u1","createdAt":"2026-08-01T01:00:00Z","deletedBy":null,"deletedAt":null,"amount":"500.00"}"""

        const val DAY_BREAKDOWN_JSON =
            """{"id":"bd1","remittanceId":"r1","branchDayId":"d1"}"""

        const val SUBMIT_JSON =
            """{"id":"r1","type":"SESSION","status":"SUBMITTED","branchId":"b1","method":"BANK_TRANSFER","submittedDate":"2026-08-09","submittedAt":"2026-08-09T01:00:00Z","submittedBy":"u1","dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-01T01:00:00Z","version":4,"grossIncome":"1234.56","totalCompensation":"100.00","totalExpenses":"1200.00","netIncome":"1234.56"}"""

        const val UNDO_JSON =
            """{"id":"r1","type":"SESSION","status":"DRAFT","branchId":"b1","method":"BANK_TRANSFER","submittedDate":"2026-08-09","submittedAt":null,"submittedBy":"u1","dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-01T01:00:00Z","version":4,"netIncome":null}"""

        const val PATCHED_JSON =
            """{"id":"r1","type":"PRODUCT","status":"DRAFT","branchId":"b1","method":"HANDED_TO_ACCOUNTANT","submittedDate":"","submittedAt":null,"submittedBy":"u1","dateRangeStart":"2026-08-01","dateRangeEnd":"2026-08-09","createdAt":"2026-08-01T01:00:00Z","version":4,"netIncome":null}"""

        const val DRIFT_JSON =
            """{
                "frozen":{"remittanceId":"r1","grossIncome":"1234.56","totalCompensation":"100.00","totalExpenses":"1200.00","netIncome":"1200.00","snapshottedAt":"2026-08-09T01:00:00Z"},
                "currentCompensation":"100.00","currentExpenses":"1350.00","currentNet":"1150.00"
            }"""
    }
}
