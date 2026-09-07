package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.SessionState
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * #94-grad — the BranchSelect surface VM: GET /api/me/branches (the #98 data source) and the
 * Phase-3 clock-in chain (POST clock-in → SessionState.setClockedIn → ADR-0021 capability
 * refresh → Success on refreshState, which is what the screen navigates on). The refresh
 * stores the FULL row list (#156; the client-side branch slice filter is gone). A failed
 * clock-in must NOT write the selected branch or fire the refresh; a failed
 * refresh must leave capabilities untouched (retry = refresh only, never re-clock-in).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BranchSelectViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        SessionState.clear()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        SessionState.clear()
    }

    private val branch =
        MeBranchResponse(
            branchId = "b1",
            branchName = "Main Branch",
            branchType = BranchType.CLINIC,
            clockInStatus = BranchClockInStatus.NOT_CLOCKED_IN,
            isRelief = false,
            assignmentId = "a1",
        )

    private val branchesJson =
        """
        [
          {"branchId":"b1","branchName":"Main Branch","branchType":"CLINIC","clockInStatus":"NOT_CLOCKED_IN","isRelief":false},
          {"branchId":"b2","branchName":"Tour Branch","branchType":"PROVINCIAL_TOUR","clockInStatus":"CLOCKED_IN_HERE","isRelief":true}
        ]
        """.trimIndent()

    private val capabilitiesJson =
        """
        [
          {"capabilityCode":"MANAGE_USERS","contextType":"GLOBAL","contextId":"00000000-0000-0000-0000-000000000000","sourceType":"ROLE"},
          {"capabilityCode":"SUBMIT_REMITTANCE","contextType":"BRANCH","contextId":"b1","sourceType":"MANUAL_OVERRIDE"},
          {"capabilityCode":"EDIT_BRANCH_DATA","contextType":"BRANCH","contextId":"b2","sourceType":"MANUAL_OVERRIDE"}
        ]
        """.trimIndent()

    private val clockInJson =
        """{"id":"a1","branchDayId":"d1","userId":"u1","markedBy":"u1","clockIn":"2026-08-10T08:00:00+08:00","clockOut":null,"isRelief":false}"""

    @Test
    fun loadBranches_success_emits_list() =
        runTest(testScheduler) {
            val vm = BranchSelectViewModel(mockApiClient(branchSelectHandler()))

            vm.loadBranches()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<MeBranchResponse>>>(vm.branches.value)
            assertEquals(listOf("b1", "b2"), state.data.map { it.branchId })
            assertEquals(BranchClockInStatus.CLOCKED_IN_HERE, state.data[1].clockInStatus)
            assertTrue(state.data[1].isRelief)
        }

    @Test
    fun loadBranches_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                BranchSelectViewModel(
                    mockApiClient(branchSelectHandler(branchesStatus = HttpStatusCode.Forbidden)),
                )

            vm.loadBranches()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.branches.value)
        }

    @Test
    fun clockIn_success_writes_selected_branch_and_refreshes_full_list() =
        runTest(testScheduler) {
            val vm = BranchSelectViewModel(mockApiClient(branchSelectHandler()))

            vm.clockIn(branch)
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(vm.clockInState.value)
            assertEquals(
                "b1",
                SessionState.snapshot.value.clock
                    ?.branchId,
            )
            assertEquals(
                "Main Branch",
                SessionState.snapshot.value.clock
                    ?.branchName,
            )
            // #147 — the clock-state slots persist for the drawer's clock-out request.
            assertEquals(
                "a1",
                SessionState.snapshot.value.clock
                    ?.attendanceId,
            )
            assertEquals(
                "d1",
                SessionState.snapshot.value.clock
                    ?.branchDayId,
            )
            // ADR-0021 second trigger — the full row list is stored (#156), including rows
            // outside the selected branch (b2) and other contexts.
            assertIs<UiState.Success<Unit>>(vm.refreshState.value)
            val caps = SessionState.snapshot.value.capabilities
            assertEquals(3, caps.size)
            assertEquals(
                listOf("MANAGE_USERS", "SUBMIT_REMITTANCE", "EDIT_BRANCH_DATA"),
                caps.map { it.capabilityCode },
            )
            assertEquals(listOf("GLOBAL", "BRANCH", "BRANCH"), caps.map { it.contextType.name })
            assertEquals(listOf("b1", "b2"), caps.filter { it.contextType.name == "BRANCH" }.map { it.contextId })
        }

    @Test
    fun clockIn_failure_does_not_write_branch_or_refresh() =
        runTest(testScheduler) {
            var capsCalls = 0
            val handler = branchSelectHandler(clockInStatus = HttpStatusCode.Conflict)
            val countingHandler: MockRequestHandler = { request ->
                if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/me/capabilities") {
                    capsCalls++
                }
                handler(request)
            }
            val vm = BranchSelectViewModel(mockApiClient(countingHandler))

            vm.clockIn(branch)
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.clockInState.value)
            assertEquals(
                null,
                SessionState.snapshot.value.clock
                    ?.branchId,
            )
            assertEquals(emptyList<UserCapabilityResponse>(), SessionState.snapshot.value.capabilities)
            assertEquals(0, capsCalls)
        }

    @Test
    fun refresh_failure_keeps_capabilities_untouched() =
        runTest(testScheduler) {
            val vm =
                BranchSelectViewModel(
                    mockApiClient(
                        branchSelectHandler(capsStatus = HttpStatusCode.InternalServerError),
                    ),
                )

            vm.clockIn(branch)
            advanceUntilIdle()

            // The clock-in itself succeeded (branch written) but the refresh failed —
            // retry semantics: refresh only, capabilities from the previous fetch stay.
            assertIs<UiState.Success<Unit>>(vm.clockInState.value)
            assertEquals(
                "b1",
                SessionState.snapshot.value.clock
                    ?.branchId,
            )
            assertIs<UiState.Error>(vm.refreshState.value)
            assertEquals(emptyList<UserCapabilityResponse>(), SessionState.snapshot.value.capabilities)
        }

    @Test
    fun clockIn_ignores_second_tap_while_in_flight() =
        runTest(testScheduler) {
            var clockInCalls = 0
            val handler: MockRequestHandler = { request ->
                if (request.url.encodedPath == "/api/attendance/clock-in") {
                    clockInCalls++
                }
                branchSelectHandler()(request)
            }
            val vm = BranchSelectViewModel(mockApiClient(handler))

            vm.clockIn(branch)
            vm.clockIn(branch)
            advanceUntilIdle()

            assertEquals(1, clockInCalls)
            assertEquals(
                "b1",
                SessionState.snapshot.value.clock
                    ?.branchId,
            )
        }

    private fun branchSelectHandler(
        branchesStatus: HttpStatusCode = HttpStatusCode.OK,
        clockInStatus: HttpStatusCode = HttpStatusCode.Created,
        capsStatus: HttpStatusCode = HttpStatusCode.OK,
    ): MockRequestHandler =
        { request ->
            when (request.url.encodedPath) {
                "/api/me/branches" -> {
                    respond(
                        content = ByteReadChannel(branchesJson),
                        status = branchesStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                "/api/attendance/clock-in" -> {
                    respond(
                        content = ByteReadChannel(clockInJson),
                        status = clockInStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                "/api/me/capabilities" -> {
                    respond(
                        content = ByteReadChannel(capabilitiesJson),
                        status = capsStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> {
                    respond("", HttpStatusCode.NotFound)
                }
            }
        }
}
