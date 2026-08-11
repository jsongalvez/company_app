package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.dto.AuditLogTableResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the Audit Log screen's ViewModel state model (#123 build of the locked #104
 * D1-D10): flagged load, pessimistic acknowledge (row removed in place on 2xx; kept with an
 * inline per-row error on failure incl. the server-enforced self-ack 409), filtered browse with
 * keyset cursor pagination (append on load-more, silent refresh that keeps the accumulated
 * list), server-driven tables, and per-record history.
 *
 * Uses the #93 handler-based MockEngine, `runTest(testScheduler)` + `StandardTestDispatcher`
 * main, and `runCurrent` drain (same shape as NotificationViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {
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
    fun loadFlaggedEntries_success_emits_success_list() =
        runTest(testScheduler) {
            val vm = AuditLogViewModel(mockApiClient(AuditHarness().handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
        }

    @Test
    fun loadFlaggedEntries_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                AuditLogViewModel(
                    mockApiClient(AuditHarness(flaggedStatus = HttpStatusCode.InternalServerError).handler()),
                )

            vm.loadFlaggedEntries()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.flaggedEntries.value)
        }

    @Test
    fun acknowledge_success_removes_row_in_place() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e2"), actual = state.data.map { it.id })
            assertEquals(expected = 1, actual = harness.ackCount)
            assertTrue(vm.acknowledgingIds.value.isEmpty())
        }

    @Test
    fun acknowledge_failure_keeps_row_with_inline_error() =
        runTest(testScheduler) {
            val vm =
                AuditLogViewModel(
                    mockApiClient(AuditHarness(ackStatus = HttpStatusCode.InternalServerError).handler()),
                )

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            val error = vm.ackErrors.value["e1"]
            assertEquals(expected = "Acknowledge failed: 500", actual = error)
            assertTrue(vm.acknowledgingIds.value.isEmpty())
        }

    @Test
    fun acknowledge_self_conflict_keeps_row_with_reviewer_message() =
        runTest(testScheduler) {
            val vm =
                AuditLogViewModel(
                    mockApiClient(AuditHarness(ackStatus = HttpStatusCode.Conflict).handler()),
                )

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(
                expected = "Only another reviewer can acknowledge this entry",
                actual = vm.ackErrors.value["e1"],
            )
        }

    @Test
    fun acknowledge_double_tap_fires_single_request() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.ackCount)
        }

    @Test
    fun acknowledge_malformed_response_clears_inflight_and_sets_inline_error() =
        runTest(testScheduler) {
            val harness = AuditHarness(ackBody = "not-json")
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            // The button must re-enable and the row must stay with an inline error (ADR-0022
            // pessimistic axis) — the deserialization failure must not freeze the in-flight set.
            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertTrue(vm.acknowledgingIds.value.isEmpty())
            assertTrue(vm.ackErrors.value.containsKey("e1"))
        }

    @Test
    fun acknowledge_success_clears_browse_row_flag() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.refreshBrowse()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()

            // For-review: row removed in place (D2). All-activity: the row keeps its position but
            // the flag clears — the badge + Acknowledge affordance disappear without a refresh
            // (D10 in-place semantics; a re-tap would otherwise 404 on the already-acked row).
            val flagged = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e2"), actual = flagged.data.map { it.id })
            val browse = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = browse.data.map { it.id })
            assertFalse(browse.data.first { it.id == "e1" }.isFlagged)
        }

    @Test
    fun loadBrowse_failure_is_loud_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            harness.browseFailure = true
            vm.loadBrowse()
            advanceUntilIdle()

            // D10 cold-loud path: a first-visit failure is an error card, not an endless spinner
            // with a one-line refresh error (the pre-fix silent-refresh first visit).
            assertIs<UiState.Error>(vm.browseEntries.value)
            assertFalse(vm.isRefreshing.value)
        }

    @Test
    fun applyFilters_ignores_stale_load_more_page() =
        runTest(testScheduler) {
            val harness = AuditHarness(browseBodies = mutableListOf(PAGE1_JSON, PAGE2_JSON, PAGE1_JSON))
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            val staleGate = CompletableDeferred<Unit>()
            val coldGate = CompletableDeferred<Unit>()
            harness.entryGates.addLast(staleGate)
            harness.entryGates.addLast(coldGate)
            vm.loadMore()
            advanceUntilIdle()
            vm.applyFilters(AuditLogFilters(tableName = "session"))
            advanceUntilIdle()

            // The new-generation cold page lands first, then the stale load-more page: it must
            // neither append to the new list nor overwrite the new cursor (pass-1 HARD: a stale
            // page used to mix filter generations on list + cursor).
            coldGate.complete(Unit)
            advanceUntilIdle()
            staleGate.complete(Unit)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = "c1", actual = vm.nextCursor.value)
            assertFalse(vm.isLoadingMore.value)
        }

    @Test
    fun applyFilters_ignores_stale_refresh_page() =
        runTest(testScheduler) {
            val harness = AuditHarness(browseBodies = mutableListOf(PAGE1_JSON, PAGE1_JSON, PAGE3_JSON))
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            val staleGate = CompletableDeferred<Unit>()
            val coldGate = CompletableDeferred<Unit>()
            harness.entryGates.addLast(staleGate)
            harness.entryGates.addLast(coldGate)
            vm.refreshBrowse()
            advanceUntilIdle()
            vm.applyFilters(AuditLogFilters(tableName = "session"))
            advanceUntilIdle()

            // A stale silent-refresh page landing after the new cold page must not replace the
            // new-filter list with the old filter's data.
            coldGate.complete(Unit)
            advanceUntilIdle()
            staleGate.complete(Unit)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e9"), actual = state.data.map { it.id })
            assertFalse(vm.isRefreshing.value)
        }

    @Test
    fun loadHistory_not_found_shows_trail_message() =
        runTest(testScheduler) {
            val harness = AuditHarness(historyStatus = HttpStatusCode.NotFound)
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadHistory(tableName = "session", recordId = "r1")
            advanceUntilIdle()

            // 404 = the record has no audit trail — a truthful message, not "history failed: 404"
            // (an absent record is not an outage).
            val state = assertIs<UiState.Error>(vm.history.value)
            assertEquals(expected = "No audit trail found for this record", actual = state.message)
        }

    @Test
    fun loadMore_malformed_response_keeps_list_and_reports_error() =
        runTest(testScheduler) {
            val harness = AuditHarness(browseBodies = mutableListOf(PAGE1_JSON, "not-json"))
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertTrue(vm.loadMoreError.value != null)
            assertFalse(vm.isLoadingMore.value)
        }

    @Test
    fun refreshBrowse_network_failure_keeps_list_and_reports_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            harness.browseFailure = true
            vm.refreshBrowse()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertTrue(vm.browseRefreshError.value != null)
            assertFalse(vm.isRefreshing.value)
        }

    @Test
    fun refreshFlagged_double_tap_fires_single_request() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshFlagged()
            vm.refreshFlagged()
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.flaggedCount)
        }

    @Test
    fun refreshFlagged_failure_keeps_list_and_sets_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            harness.flaggedStatus = HttpStatusCode.InternalServerError
            vm.refreshFlagged()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = "flagged load failed: 500", actual = vm.flaggedRefreshError.value)
        }

    @Test
    fun refreshFlagged_malformed_response_clears_inflight_and_reports_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            harness.flaggedBody = "not-json"
            vm.refreshFlagged()
            advanceUntilIdle()

            // The guard must clear on the deserialization failure (the list stays rendered) and
            // the error must surface on the For-review tab's refresh line.
            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertTrue(vm.flaggedRefreshError.value != null)
            assertEquals(expected = 2, actual = harness.flaggedCount)
            assertFalse(vm.isRefreshing.value)

            // A later refresh must not silently no-op: the guard is clear, so the request fires.
            harness.flaggedBody = FLAGGED_JSON
            vm.refreshFlagged()
            advanceUntilIdle()
            assertEquals(expected = 3, actual = harness.flaggedCount)
        }

    @Test
    fun loadFlaggedEntries_failure_clears_inflight_and_retry_fires_again() =
        runTest(testScheduler) {
            // 400 (not 5xx): HttpRequestRetry would re-attempt a 500, skewing the request count.
            val harness = AuditHarness(flaggedStatus = HttpStatusCode.BadRequest)
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            assertIs<UiState.Error>(vm.flaggedEntries.value)

            harness.flaggedStatus = HttpStatusCode.OK
            vm.loadFlaggedEntries()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = 2, actual = harness.flaggedCount)
        }

    @Test
    fun refreshFlagged_skipped_while_ack_in_flight() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            // No advance: the ack is in flight, so a re-entry reload must not fire a GET whose
            // pre-commit snapshot could resurrect the just-acked row.
            vm.refreshFlagged()
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.flaggedCount)
            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e2"), actual = state.data.map { it.id })
        }

    @Test
    fun refreshFlagged_excludes_entries_acknowledged_this_session() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadFlaggedEntries()
            advanceUntilIdle()
            vm.acknowledge(entry("e1"))
            advanceUntilIdle()
            // The harness still serves e1 as flagged (it doesn't model the server-side ack) — the
            // VM must drop the locally-acknowledged id from any later snapshot.
            vm.refreshFlagged()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.flaggedEntries.value)
            assertEquals(expected = listOf("e2"), actual = state.data.map { it.id })
        }

    @Test
    fun applyFilters_replaces_list_with_filtered_page_and_sends_params() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.applyFilters(
                AuditLogFilters(
                    tableName = "session",
                    action = "UPDATE",
                    callerName = "ana",
                    dateFrom = "2026-08-01",
                    dateTo = "2026-08-05",
                ),
            )
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = 1, actual = harness.entriesRequests.size)
            val params =
                harness.entriesRequests
                    .single()
                    .parameters
            assertEquals(expected = "session", actual = params["tableName"] ?: "")
            assertEquals(expected = "UPDATE", actual = params["action"] ?: "")
            assertEquals(expected = "ana", actual = params["callerName"] ?: "")
            assertEquals(expected = "2026-08-01", actual = params["dateFrom"] ?: "")
            assertEquals(expected = "2026-08-05", actual = params["dateTo"] ?: "")
            assertNull(params["cursor"])
        }

    @Test
    fun applyFilters_blanks_are_omitted_from_request() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.applyFilters(AuditLogFilters(callerName = "   ", dateFrom = ""))
            advanceUntilIdle()

            val params =
                harness.entriesRequests
                    .single()
                    .parameters
            assertNull(params["callerName"])
            assertNull(params["dateFrom"])
        }

    @Test
    fun loadMore_appends_page_and_forwards_cursor() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2", "e3"), actual = state.data.map { it.id })
            assertEquals(expected = 2, actual = harness.entriesRequests.size)
            assertEquals(expected = "c1", actual = harness.entriesRequests[1].parameters["cursor"] ?: "")
            assertNull(vm.nextCursor.value)
        }

    @Test
    fun loadMore_without_cursor_makes_no_request() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadMore()
            advanceUntilIdle()

            assertTrue(harness.entriesRequests.isEmpty())
        }

    @Test
    fun loadMore_failure_keeps_list_and_sets_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            harness.browseStatus = HttpStatusCode.InternalServerError
            vm.loadMore()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = "browse failed: 500", actual = vm.loadMoreError.value)
            assertFalse(vm.isLoadingMore.value)
        }

    @Test
    fun refreshBrowse_keeps_list_rendered_and_replaces_with_fresh_page() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            vm.refreshBrowse()
            advanceUntilIdle()

            assertEquals(expected = 2, actual = harness.entriesRequests.size)
            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e3"), actual = state.data.map { it.id })
            assertFalse(vm.isRefreshing.value)
        }

    @Test
    fun refreshBrowse_failure_keeps_list_and_sets_refresh_error() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.refreshBrowse()
            advanceUntilIdle()
            harness.browseStatus = HttpStatusCode.InternalServerError
            vm.refreshBrowse()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.browseEntries.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            assertEquals(expected = "browse failed: 500", actual = vm.browseRefreshError.value)
        }

    @Test
    fun loadTables_success_emits_registry() =
        runTest(testScheduler) {
            val vm = AuditLogViewModel(mockApiClient(AuditHarness().handler()))

            vm.loadTables()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogTableResponse>>>(vm.tables.value)
            assertEquals(expected = listOf("session", "branch_day"), actual = state.data.map { it.tableName })
        }

    @Test
    fun loadHistory_success_fetches_per_record_with_params() =
        runTest(testScheduler) {
            val harness = AuditHarness()
            val vm = AuditLogViewModel(mockApiClient(harness.handler()))

            vm.loadHistory(tableName = "session", recordId = "r1")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<AuditLogEntryResponse>>>(vm.history.value)
            assertEquals(expected = listOf("e1", "e2"), actual = state.data.map { it.id })
            val request = harness.historyRequests.single()
            assertEquals(expected = "session", actual = request.parameters["tableName"] ?: "")
            assertEquals(expected = "r1", actual = request.parameters["recordId"] ?: "")
        }

    private fun entry(id: String): AuditLogEntryResponse =
        AuditLogEntryResponse(
            id = id,
            tableName = "session",
            recordId = "r1",
            action = "UPDATE",
            changedBy = "u1",
            changedByName = "Ana",
            changedAt = "2026-08-05T06:00:00+08:00",
            oldValue = """{"status":"OPEN"}""",
            newValue = """{"status":"REMITTED"}""",
            isFlagged = true,
            reason = "Post-closing edit",
        )

    private class AuditHarness(
        var flaggedStatus: HttpStatusCode = HttpStatusCode.OK,
        var ackStatus: HttpStatusCode = HttpStatusCode.OK,
        var browseStatus: HttpStatusCode = HttpStatusCode.OK,
        var historyStatus: HttpStatusCode = HttpStatusCode.OK,
        var ackBody: String = ENTRY_JSON,
        browseBodies: List<String> = listOf(PAGE1_JSON, PAGE2_JSON),
    ) {
        var ackCount: Int = 0
        var flaggedCount: Int = 0
        var flaggedBody: String = FLAGGED_JSON

        // When true the entries handler throws — a network failure before any response.
        var browseFailure: Boolean = false

        // Per-call gates: when non-empty, the entries handler awaits the next gate before
        // responding — lets a test control which in-flight browse request lands first (the
        // #144 pass-1 stale-generation race).
        val entryGates = ArrayDeque<CompletableDeferred<Unit>>()
        private val pageBodies = browseBodies.toMutableList()
        private var browseCall = 0
        val entriesRequests = mutableListOf<io.ktor.http.Url>()
        val historyRequests = mutableListOf<io.ktor.http.Url>()

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get &&
                        request.url.encodedPath == "/api/audit-log/flagged" -> {
                        flaggedCount++
                        jsonRespond(flaggedStatus, flaggedBody)
                    }

                    request.method == HttpMethod.Patch &&
                        request.url.encodedPath.startsWith("/api/audit-log/") &&
                        request.url.encodedPath.endsWith("/acknowledge") -> {
                        ackCount++
                        jsonRespond(ackStatus, ackBody)
                    }

                    request.method == HttpMethod.Get &&
                        request.url.encodedPath == "/api/audit-log/entries" -> {
                        if (browseFailure) {
                            throw IOException("network down")
                        }
                        // Body index assigned on ENTRY (before the gate await): a gated request
                        // holds its slot even while suspended, so a later request can't take it.
                        entriesRequests += request.url
                        val body = pageBodies.getOrElse(browseCall) { pageBodies.last() }
                        browseCall++
                        entryGates.removeFirstOrNull()?.await()
                        jsonRespond(browseStatus, body)
                    }

                    request.method == HttpMethod.Get &&
                        request.url.encodedPath == "/api/audit-log/tables" -> {
                        jsonRespond(HttpStatusCode.OK, TABLES_JSON)
                    }

                    request.method == HttpMethod.Get &&
                        request.url.encodedPath == "/api/audit-log" -> {
                        historyRequests += request.url
                        jsonRespond(historyStatus, FLAGGED_JSON)
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
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
        const val FLAGGED_JSON =
            """[
                {"id":"e1","tableName":"session","recordId":"r1","action":"UPDATE","changedBy":"u1","changedByName":"Ana","branchId":null,"changedAt":"2026-08-05T06:00:00+08:00","oldValue":"{\"status\":\"OPEN\"}","newValue":"{\"status\":\"REMITTED\"}","isFlagged":true,"reason":"Post-closing edit","acknowledgedBy":null,"acknowledgedAt":null},
                {"id":"e2","tableName":"branch_day","recordId":"r2","action":"DELETE","changedBy":"u2","changedByName":"Ben","branchId":null,"changedAt":"2026-08-04T03:00:00+08:00","oldValue":"{\"note\":\"old\"}","newValue":null,"isFlagged":true,"reason":"Removed","acknowledgedBy":null,"acknowledgedAt":null}
            ]"""

        const val ENTRY_JSON =
            """{"id":"e1","tableName":"session","recordId":"r1","action":"UPDATE","changedBy":"u1","changedByName":"Ana","branchId":null,"changedAt":"2026-08-05T06:00:00+08:00","oldValue":"{\"status\":\"OPEN\"}","newValue":"{\"status\":\"REMITTED\"}","isFlagged":true,"reason":"Post-closing edit","acknowledgedBy":"u9","acknowledgedAt":"2026-08-05T07:00:00+08:00"}"""

        const val PAGE1_JSON =
            """{"entries":[
                {"id":"e1","tableName":"session","recordId":"r1","action":"UPDATE","changedBy":"u1","changedByName":"Ana","branchId":null,"changedAt":"2026-08-05T06:00:00+08:00","oldValue":"{\"status\":\"OPEN\"}","newValue":"{\"status\":\"REMITTED\"}","isFlagged":true,"reason":null,"acknowledgedBy":null,"acknowledgedAt":null},
                {"id":"e2","tableName":"branch_day","recordId":"r2","action":"DELETE","changedBy":"u2","changedByName":"Ben","branchId":null,"changedAt":"2026-08-04T03:00:00+08:00","oldValue":"{\"note\":\"old\"}","newValue":null,"isFlagged":false,"reason":null,"acknowledgedBy":null,"acknowledgedAt":null}
            ],"nextCursor":"c1"}"""

        const val PAGE2_JSON =
            """{"entries":[
                {"id":"e3","tableName":"client","recordId":"r3","action":"INSERT","changedBy":"u3","changedByName":"Cal","branchId":null,"changedAt":"2026-08-03T01:00:00+08:00","oldValue":null,"newValue":"{\"name\":\"New\"}","isFlagged":false,"reason":null,"acknowledgedBy":null,"acknowledgedAt":null}
            ],"nextCursor":null}"""

        const val PAGE3_JSON =
            """{"entries":[
                {"id":"e9","tableName":"session","recordId":"r9","action":"UPDATE","changedBy":"u1","changedByName":"Ana","branchId":null,"changedAt":"2026-08-06T06:00:00+08:00","oldValue":"{\"status\":\"OPEN\"}","newValue":"{\"status\":\"PAST\"}","isFlagged":false,"reason":null,"acknowledgedBy":null,"acknowledgedAt":null}
            ],"nextCursor":null}"""

        const val TABLES_JSON =
            """[
                {"tableName":"session","label":"Sessions"},
                {"tableName":"branch_day","label":"Branch days"}
            ]"""
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
