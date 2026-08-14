package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.screen.ReportMode
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
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BRANCH_A = "branch-a"
private const val BRANCH_B = "branch-b"
private const val DAY_ID = "day-1"

private val NOW = kotlinx.datetime.Instant.parse("2026-08-14T09:00:00+08:00")

private const val BRANCHES_JSON =
    """[{"id":"branch-a","name":"Branch A","branchType":"CLINIC"},
        {"id":"branch-b","name":"Branch B","branchType":"CLINIC"}]"""

private fun summaryRow(
    date: String,
    branchDayId: String = DAY_ID,
    gross: String = "1000.00",
) = DailySalesSummaryResponse(
    branchDayId = branchDayId,
    branchId = BRANCH_A,
    date = date,
    grossIncome = gross,
    totalCompensation = "200.00",
    totalExpenses = "50.00",
    netIncome = "750.00",
    totalProductSales = "300.00",
    totalCommission = "10.0000",
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceReportsViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        SessionState.clear()
        SessionState.setSelectedBranch(BRANCH_A, "Branch A")
        // The pass-3/4 per-element gates skip section loads without the capability.
        SessionState.setCapabilities(setOf("EDIT_BRANCH_DATA", "ASSIGN_COMPENSATION"))
    }

    @AfterTest
    fun teardown() {
        SessionState.clear()
        Dispatchers.resetMain()
    }

    private fun MockRequestHandleScope.respondJson(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData =
        respond(
            content = ByteReadChannel(json),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.respondCsv(): HttpResponseData =
        respond(
            content = ByteReadChannel("date,gross\n2026-08-14,1000.00"),
            status = HttpStatusCode.OK,
            headers =
                headersOf(
                    Pair(HttpHeaders.ContentType, listOf(ContentType.Text.CSV.toString())),
                    Pair(
                        HttpHeaders.ContentDisposition,
                        listOf("attachment; filename=\"daily-export-branch-a-2026-08-14.csv\""),
                    ),
                ),
        )

    private fun feedResponse(
        dates: List<String>,
        nextCursor: String? = null,
    ): String {
        val entries =
            dates.joinToString(",") { date ->
                """{"branchDayId":"$DAY_ID","branchId":"$BRANCH_A","date":"$date",
                    "grossIncome":"1000.00","totalCompensation":"200.00","totalExpenses":"50.00",
                    "netIncome":"750.00","totalProductSales":"300.00","totalCommission":"10.0000"}"""
            }
        val body = """{"entries":[$entries]"""
        return if (nextCursor != null) {
            """$body,"nextCursor":"$nextCursor"}"""
        } else {
            "$body}"
        }
    }

    private fun expenseJson(
        id: String,
        deleted: Boolean = false,
        deletedReason: String? = null,
    ): String {
        val del =
            if (deleted) {
                """"deletedBy":"u1","deletedAt":"2026-08-14T10:00:00+08:00","deletedReason":"$deletedReason","""
            } else {
                """"deletedBy":null,"deletedAt":null,"deletedReason":null,"""
            }
        return """{"id":"$id","branchDayId":"$DAY_ID","amount":"100.00","category":"PANTRY",
            "notes":null,"createdBy":"u1","createdAt":"2026-08-14T08:00:00+08:00",$del"version":1}"""
    }

    // ─────────────────────────── branches + feed ───────────────────────────

    @Test
    fun branchesLoad_autoSelectsClockedInBranchAndLoadsFeed() =
        runTest(testScheduler) {
            var feedRequests = 0
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        feedRequests++
                        // DAILY mode pins `to` = today
                        assertTrue(request.url.parameters["to"] != null)
                        assertEquals("2026-08-14", request.url.parameters["to"])
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)

            vm.loadBranches()
            runCurrent()

            assertEquals(BRANCH_A, vm.selectedBranchId.value)
            assertEquals(1, feedRequests)
            val feed = vm.feedEntries.value
            assertIs<UiState.Success<List<DailySalesSummaryResponse>>>(feed)
            assertEquals(1, feed.data.size)
        }

    @Test
    fun branchesLoad_accountantWithoutSelectedBranch_picksFirstBranch() =
        runTest(testScheduler) {
            SessionState.clear()
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath.endsWith("/daily-summaries") -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)

            vm.loadBranches()
            runCurrent()

            assertEquals(BRANCH_A, vm.selectedBranchId.value)
        }

    @Test
    fun selectBranch_recoldsTheFeedForTheNewBranch() =
        runTest(testScheduler) {
            val requested = mutableListOf<String>()
            val handler: MockRequestHandler = { request ->
                when (request.url.encodedPath) {
                    "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    "/api/branches/$BRANCH_A/daily-summaries" -> {
                        requested += BRANCH_A
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    "/api/branches/$BRANCH_B/daily-summaries" -> {
                        requested += BRANCH_B
                        respondJson(feedResponse(listOf("2026-08-13")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            assertEquals(listOf(BRANCH_A), requested)

            vm.selectBranch(BRANCH_B)
            runCurrent()

            assertEquals(listOf(BRANCH_A, BRANCH_B), requested)
            val feed = vm.feedEntries.value
            assertIs<UiState.Success<List<DailySalesSummaryResponse>>>(feed)
            assertEquals("2026-08-13", feed.data.single().date)
        }

    @Test
    fun monthlyMode_sendsMonthBoundsAsWindow() =
        runTest(testScheduler) {
            val windows = mutableListOf<Pair<String?, String?>>()
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        windows += (request.url.parameters["from"] to request.url.parameters["to"])
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.MONTHLY)
            runCurrent()

            // DAILY window first (to=today), then MONTHLY window (Aug 2026 bounds)
            assertEquals("2026-08-01" to "2026-08-31", windows.last())
        }

    @Test
    fun dateRangeMode_invalidRangeShowsParamErrorWithoutRefetch() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.DATE_RANGE)
            runCurrent()
            vm.setRangeInputs("2026-08-20", "2026-08-01")
            vm.applyRange()
            runCurrent()

            assertEquals("From must be on or before To", vm.paramError.value)
            assertNull(vm.appliedRange.value)
        }

    @Test
    fun dateRangeMode_validRangeSendsWindowParams() =
        runTest(testScheduler) {
            val windows = mutableListOf<Pair<String?, String?>>()
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        windows += (request.url.parameters["from"] to request.url.parameters["to"])
                        respondJson(feedResponse(listOf("2026-08-10")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.DATE_RANGE)
            runCurrent()
            vm.setRangeInputs("2026-08-01", "2026-08-10")
            vm.applyRange()
            runCurrent()

            assertEquals("2026-08-01" to "2026-08-10", windows.last())
        }

    @Test
    fun loadMore_appendsAndStopsAtLastPage() =
        runTest(testScheduler) {
            var page = 0
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        page++
                        if (page == 1) {
                            respondJson(feedResponse(listOf("2026-08-14"), nextCursor = "c1"))
                        } else {
                            respondJson(feedResponse(listOf("2026-08-13")))
                        }
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.loadMore()
            runCurrent()

            val feed = vm.feedEntries.value
            assertIs<UiState.Success<List<DailySalesSummaryResponse>>>(feed)
            assertEquals(listOf("2026-08-14", "2026-08-13"), feed.data.map { it.date })
            assertNull(vm.nextCursor.value)

            vm.loadMore()
            runCurrent()
            assertEquals(2, page, "no cursor → loadMore no-ops")
        }

    @Test
    fun monthlyRollup404_landsSuccessNull() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/monthly-summary" -> {
                        respondJson("""{"error":"No data"}""", HttpStatusCode.NotFound)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.MONTHLY)
            runCurrent()

            val rollup = vm.monthlyRollup.value
            assertIs<UiState.Success<MonthlyRemittanceSummaryResponse?>>(rollup)
            assertNull(rollup.data, "404 = no rollup, not an error (#105 F5)")
        }

    // ─────────────────────────── edit mode ───────────────────────────

    @Test
    fun editMode_loadsAllFourSectionsForTheSelectedDay() =
        runTest(testScheduler) {
            SessionState.setCapabilities(setOf("ASSIGN_COMPENSATION", "EDIT_BRANCH_DATA"))
            val paths = mutableListOf<String>()
            val handler: MockRequestHandler = { request ->
                paths += request.url.encodedPath
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" -> {
                        respondJson(
                            "[${expenseJson(
                                "e1",
                            )},${expenseJson("e2", deleted = true, deletedReason = "Wrong entry")}]",
                        )
                    }

                    request.url.encodedPath == "/api/compensations" -> {
                        respondJson(
                            """[{"id":"c1","workBranchDayId":"$DAY_ID","payingBranchDayId":"$DAY_ID","userId":"u1","userName":"Pract 1","amount":"500.00","assignedBy":"u2","assignedAt":"2026-08-14T08:00:00+08:00","note":null,"version":1}]""",
                        )
                    }

                    request.url.encodedPath == "/api/allowances" -> {
                        respondJson(
                            """[{"id":"a1","branchDayId":"$DAY_ID","userId":"u1","amount":"100.00","assignedBy":"u2","assignedAt":"2026-08-14T08:00:00+08:00"}]""",
                        )
                    }

                    request.url.encodedPath == "/api/branch-days/$DAY_ID/users" -> {
                        respondJson(
                            """[{"userId":"u1","displayName":"Pract 1"},{"userId":"u2","displayName":"Pract 2"}]""",
                        )
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val feed = vm.feedEntries.value
            val day = (feed as UiState.Success).data.single()

            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()

            val expenses = vm.editExpenses.value
            assertIs<UiState.Success<List<ExpenseResponse>>>(expenses)
            assertEquals(2, expenses.data.size)
            val deleted = expenses.data.first { it.id == "e2" }
            assertTrue(deleted.deletedAt != null, "soft-deleted rows ride the GET (#153 Q1)")
            assertEquals("Wrong entry", deleted.deletedReason)
            assertIs<UiState.Success<List<*>>>(vm.editCompensations.value)
            assertIs<UiState.Success<List<*>>>(vm.editAllowances.value)
            assertIs<UiState.Success<List<*>>>(vm.editUsers.value)
        }

    @Test
    fun createExpense_appendsRowAndClearsInFlight() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" -> {
                        when (request.method.value) {
                            "GET" -> respondJson("[]")
                            else -> respondJson(expenseJson("e-new"))
                        }
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()

            vm.createExpense(amount = "250.00", categoryCode = "WATER", notes = "Bottles", reason = null)
            runCurrent()

            val expenses = vm.editExpenses.value
            assertIs<UiState.Success<List<ExpenseResponse>>>(expenses)
            assertEquals("e-new", expenses.data.single().id)
            assertTrue(vm.inFlightActions.value.isEmpty())
        }

    @Test
    fun updateExpense409_showsConflictErrorAndReloadsSection() =
        runTest(testScheduler) {
            var sectionLoads = 0
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" && request.method.value == "GET" -> {
                        sectionLoads++
                        respondJson("[${expenseJson("e1")}]")
                    }

                    request.url.encodedPath == "/api/expenses/e1" && request.method.value == "PATCH" -> {
                        respondJson("""{"error":"version mismatch"}""", HttpStatusCode.Conflict)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            assertEquals(1, sectionLoads)
            val expense = (vm.editExpenses.value as UiState.Success).data.single()

            vm.updateExpense(expense, amount = "999.00", categoryCode = "PANTRY", notes = null, reason = null)
            runCurrent()

            // Pass-3 contract: the 409 emits the conflict signal (the screen closes the dialog)
            // and reloads the section; the reload landing supersedes the transient error line
            // (the #143 stale-error clear — the fresh list carries no stale errors).
            assertTrue("expense:update:e1" in vm.conflicts.value, "409 → conflict signal (dialog close)")
            assertEquals(2, sectionLoads, "409 → the section reloads (ADR-0022)")
            assertTrue(vm.editErrors.value["expense:update:e1"] == null, "reload landing clears the stale error")
        }

    @Test
    fun updateExpense403_exitsSilently() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" && request.method.value == "GET" -> {
                        respondJson("[${expenseJson("e1")}]")
                    }

                    request.url.encodedPath == "/api/expenses/e1" && request.method.value == "PATCH" -> {
                        respondJson("""{"error":"forbidden"}""", HttpStatusCode.Forbidden)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            val expense = (vm.editExpenses.value as UiState.Success).data.single()

            vm.updateExpense(expense, amount = "999.00", categoryCode = "PANTRY", notes = null, reason = null)
            runCurrent()

            assertTrue(vm.editErrors.value.isEmpty(), "403 exits silently (#113 D4 / ADR-0022)")
            assertTrue(vm.inFlightActions.value.isEmpty())
        }

    @Test
    fun deleteThenRestore_flowDimsThenClearsTheRow() =
        runTest(testScheduler) {
            val deleted = mutableListOf<String>()
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" && request.method.value == "GET" -> {
                        respondJson("[${expenseJson("e1")}]")
                    }

                    request.url.encodedPath == "/api/expenses/e1" && request.method.value == "DELETE" -> {
                        deleted += "delete"
                        respondJson(expenseJson("e1", deleted = true, deletedReason = "Wrong entry"))
                    }

                    request.url.encodedPath == "/api/expenses/e1/restore" && request.method.value == "POST" -> {
                        deleted += "restore"
                        respondJson(expenseJson("e1"))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            val expense = (vm.editExpenses.value as UiState.Success).data.single()

            vm.deleteExpense(expense, reason = "Wrong entry")
            runCurrent()
            assertTrue(deleted == listOf("delete"))
            val dimmed = (vm.editExpenses.value as UiState.Success).data.single()
            assertTrue(dimmed.deletedAt != null)
            assertEquals("Wrong entry", dimmed.deletedReason)

            vm.restoreExpense(dimmed, reason = null)
            runCurrent()
            assertTrue(deleted == listOf("delete", "restore"))
            val restored = (vm.editExpenses.value as UiState.Success).data.single()
            assertNull(restored.deletedAt, "restore clears the deletion fields in place")
            assertNull(restored.deletedReason)
        }

    @Test
    fun createCompensation409_duplicateShowsInlineError() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/compensations" -> {
                        respondJson("[]")
                    }

                    request.url.encodedPath == "/api/allowances" -> {
                        respondJson("[]")
                    }

                    request.url.encodedPath == "/api/branch-days/$DAY_ID/users" -> {
                        respondJson("""[{"userId":"u1","displayName":"Pract 1"}]""")
                    }

                    request.url.encodedPath == "/api/compensation" && request.method.value == "POST" -> {
                        respondJson("""{"error":"duplicate"}""", HttpStatusCode.Conflict)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()

            vm.createCompensation(userId = "u1", amount = "500.00", note = null, reason = null)
            runCurrent()

            assertEquals("Already compensated on this day", vm.editErrors.value["comp:create"])
        }

    @Test
    fun exportDay_surfacesBytesAndFilenameFromContentDisposition() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/export/daily" -> {
                        respondCsv()
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()

            vm.exportDay(day, BRANCH_A, "csv")
            runCurrent()

            val download = vm.downloads.value["day:day-1:csv"]
            assertIs<UiState.Success<FinanceReportsViewModel.DownloadPayload>>(download)
            assertEquals("daily-export-branch-a-2026-08-14.csv", download.data.fileName)
            assertTrue(download.data.bytes.isNotEmpty())
        }

    @Test
    fun exportFailure_surfacesInlineErrorForKey() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/export/daily" -> {
                        // 4xx, not 5xx: the ApiClient's HttpRequestRetry (maxRetries=3, pre-existing)
                        // re-issues 5xx responses, and the retry chain outlives one runCurrent().
                        respondJson("""{"error":"nope"}""", HttpStatusCode.BadRequest)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()

            vm.exportDay(day, BRANCH_A, "csv")
            runCurrent()

            assertTrue(vm.downloads.value.isEmpty(), "downloads=" + vm.downloads.value)
            assertTrue(vm.exportErrors.value["day:day-1:csv"] != null, "exportErrors=" + vm.exportErrors.value)
        }

    @Test
    fun selectDay_null_deselects() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when (request.url.encodedPath) {
                    "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()

            vm.selectDay(day)
            runCurrent()
            assertEquals(day, vm.selectedDay.value)

            vm.selectDay(null)
            runCurrent()
            assertNull(vm.selectedDay.value, "deselect must emit (the mobile dialog close path)")
        }

    @Test
    fun branchSwitch_clearsEditDataAndRollup() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when (request.url.encodedPath) {
                    "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    "/api/branches/$BRANCH_B/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-13")))
                    }

                    "/api/expenses" -> {
                        respondJson("[${expenseJson("e1")}]")
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            assertIs<UiState.Success<List<ExpenseResponse>>>(vm.editExpenses.value)

            vm.selectBranch(BRANCH_B)
            runCurrent()

            assertNull(vm.selectedDay.value)
            assertTrue(vm.editMode.value == false)
            assertEquals(UiState.Idle, vm.editExpenses.value, "previous branch's edit data must not survive the switch")
            assertEquals(UiState.Idle, vm.monthlyRollup.value)
        }

    @Test
    fun modeSwitch_clearsTheStaleDaySelection() =
        runTest(testScheduler) {
            val handler: MockRequestHandler = { request ->
                when (request.url.encodedPath) {
                    "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            runCurrent()

            vm.setMode(ReportMode.MONTHLY)
            runCurrent()

            assertNull(vm.selectedDay.value, "a day outside the new window must not stay armed")
        }

    @Test
    fun exportModeCurrent_firesTheModesExport() =
        runTest(testScheduler) {
            var exportPath: String? = null
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/export/all-time" -> {
                        exportPath = request.url.toString()
                        respondCsv()
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.ALL_TIME)
            runCurrent()
            vm.exportModeCurrent("csv")
            runCurrent()

            assertTrue(exportPath?.contains("format=csv") == true, "the all-time toolbar export fires")
            val download = vm.downloads.value["mode:ALL_TIME:csv"]
            assertIs<UiState.Success<FinanceReportsViewModel.DownloadPayload>>(download)
        }

    @Test
    fun supersededUpdate_landsAfterBranchSwitch_writesNothing() =
        runTest(testScheduler) {
            // Pass-6 HARD — an in-flight PATCH completing after a branch switch must not
            // repopulate the cleared sections, trigger a reload, nor end the new session's
            // in-flight flags.
            var sectionLoads = 0
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_B/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-13")))
                    }

                    request.url.encodedPath == "/api/expenses" && request.method.value == "GET" -> {
                        sectionLoads++
                        respondJson("[${expenseJson("e1")}]")
                    }

                    request.url.encodedPath == "/api/expenses/e1" && request.method.value == "PATCH" -> {
                        respondJson(expenseJson("e1"))
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            val expense = (vm.editExpenses.value as UiState.Success).data.single()

            // Dispatch the PATCH, then switch branches BEFORE it lands.
            vm.updateExpense(expense, "999.00", "PANTRY", null, null)
            vm.selectBranch(BRANCH_B)
            runCurrent()

            assertEquals(UiState.Idle, vm.editExpenses.value, "the superseded PATCH must not repopulate")
            assertEquals(1, sectionLoads, "a superseded PATCH must not trigger a section reload")
            assertTrue(vm.inFlightActions.value.isEmpty())

            // The stronger pin (pass-7 SOFT): a fresh same-key dispatch in the NEW session
            // must keep its in-flight flag through the stale landing.
            vm.selectDay((vm.feedEntries.value as UiState.Success).data.single())
            vm.setEditMode(true)
            runCurrent()
            val expense2 = (vm.editExpenses.value as UiState.Success).data.single()
            vm.updateExpense(expense2, "999.00", "PANTRY", null, null)
            assertTrue("expense:update:${expense2.id}" in vm.inFlightActions.value, "the fresh dispatch owns its flag")
            runCurrent()
            assertTrue(vm.inFlightActions.value.isEmpty(), "the fresh dispatch completes normally")
        }

    @Test
    fun repeatConflictOnSameKey_reemits_afterConsume() =
        runTest(testScheduler) {
            var patchCount = 0
            val handler: MockRequestHandler = { request ->
                when {
                    request.url.encodedPath == "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    request.url.encodedPath == "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    request.url.encodedPath == "/api/expenses" && request.method.value == "GET" -> {
                        respondJson("[${expenseJson("e1")}]")
                    }

                    request.url.encodedPath == "/api/expenses/e1" && request.method.value == "PATCH" -> {
                        patchCount++
                        respondJson("""{"error":"version mismatch"}""", HttpStatusCode.Conflict)
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()
            val day = (vm.feedEntries.value as UiState.Success).data.single()
            vm.selectDay(day)
            vm.setEditMode(true)
            runCurrent()
            val expense = (vm.editExpenses.value as UiState.Success).data.single()

            vm.updateExpense(expense, "999.00", "PANTRY", null, null)
            runCurrent()
            assertTrue("expense:update:e1" in vm.conflicts.value)
            vm.consumeConflict("expense:update:e1")
            assertTrue(vm.conflicts.value.isEmpty())

            // A repeat conflict after consume re-emits (pass-2 HARD: the old Set-union
            // dedupe made same-key repeats invisible — the dialog would stay open).
            vm.updateExpense(expense, "999.00", "PANTRY", null, null)
            runCurrent()
            assertTrue("expense:update:e1" in vm.conflicts.value)
            assertEquals(2, patchCount)
        }

    @Test
    fun branchSwitch_inMonthlyMode_refetchesTheRollup() =
        runTest(testScheduler) {
            val monthly = mutableListOf<String>()
            val handler: MockRequestHandler = { request ->
                when (request.url.encodedPath) {
                    "/api/branches/accessible" -> {
                        respondJson(BRANCHES_JSON)
                    }

                    "/api/branches/$BRANCH_A/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-14")))
                    }

                    "/api/branches/$BRANCH_B/daily-summaries" -> {
                        respondJson(feedResponse(listOf("2026-08-13")))
                    }

                    "/api/branches/$BRANCH_A/monthly-summary" -> {
                        monthly += BRANCH_A
                        respondJson(
                            """{"branchId":"$BRANCH_A","year":2026,"month":8,"totalRemittances":1,"sessionCount":2,"productCount":0,"grossIncome":"4000.00","totalCompensation":"800.00","totalExpenses":"300.00","netIncome":"2900.00"}""",
                        )
                    }

                    "/api/branches/$BRANCH_B/monthly-summary" -> {
                        monthly += BRANCH_B
                        respondJson(
                            """{"branchId":"$BRANCH_B","year":2026,"month":8,"totalRemittances":1,"sessionCount":1,"productCount":0,"grossIncome":"1000.00","totalCompensation":"200.00","totalExpenses":"50.00","netIncome":"750.00"}""",
                        )
                    }

                    else -> {
                        respondJson("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            val vm = FinanceReportsViewModel(mockApiClient(handler), now = NOW)
            vm.loadBranches()
            runCurrent()

            vm.setMode(ReportMode.MONTHLY)
            runCurrent()
            assertEquals(listOf(BRANCH_A), monthly)

            vm.selectBranch(BRANCH_B)
            runCurrent()

            assertEquals(listOf(BRANCH_A, BRANCH_B), monthly, "branch switch refetches the pinned rollup")
            val rollup = vm.monthlyRollup.value
            assertIs<UiState.Success<MonthlyRemittanceSummaryResponse?>>(rollup)
            assertEquals(BRANCH_B, rollup.data?.branchId)
        }

    @Test
    fun hasEditCapabilities_reflectsTheCodeOnlySurface() =
        runTest(testScheduler) {
            val vm = FinanceReportsViewModel(mockApiClient(handler = { respondJson("{}") }), now = NOW)
            SessionState.clear()
            assertTrue(!vm.hasEditCapabilities())

            SessionState.setCapabilities(setOf("VIEW_BRANCH_DATA"))
            assertTrue(!vm.hasEditCapabilities(), "read-only viewer never sees the Edit toggle")

            SessionState.setCapabilities(setOf("VIEW_BRANCH_DATA", "ASSIGN_COMPENSATION"))
            assertTrue(vm.hasEditCapabilities())
        }
}
