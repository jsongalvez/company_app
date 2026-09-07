package com.companyb.companyapp.workforce.attendance

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #416 — roster assignment identity forwarding, retry, refresh, and access-loss handling. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceRosterViewModelTest {
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
    fun `slot update forwards assignment id and refreshes roster`() =
        runTest(testScheduler) {
            val harness = RosterHarness()
            val vm = AttendanceRosterViewModel(mockApiClient(harness.handler()))

            vm.updateSlot(branchId = "b1", assignmentId = "assignment-old", slot = 5)
            advanceUntilIdle()

            assertEquals(listOf("/api/branches/b1/assignments/assignment-old/slot"), harness.slotPaths)
            assertTrue("\"slot\":5" in harness.slotBodies.single())
            assertEquals(1, harness.rosterCount)
            assertIs<UiState.Success<Unit>>(vm.slotUpdate.value)
        }

    @Test
    fun `swap forwards both assignment ids`() =
        runTest(testScheduler) {
            val harness = RosterHarness()
            val vm = AttendanceRosterViewModel(mockApiClient(harness.handler()))

            vm.swapSlots(branchId = "b1", assignmentIdA = "assignment-old", assignmentIdB = "assignment-other")
            advanceUntilIdle()

            val body = harness.swapBodies.single()
            assertTrue("assignment-old" in body)
            assertTrue("assignment-other" in body)
            assertEquals(1, harness.rosterCount)
            assertIs<UiState.Success<Unit>>(vm.swapUpdate.value)
        }

    @Test
    fun `failed slot retry repeats captured assignment id`() =
        runTest(testScheduler) {
            val harness = RosterHarness(slotStatus = HttpStatusCode.NotFound)
            val vm = AttendanceRosterViewModel(mockApiClient(harness.handler()))

            vm.updateSlot(branchId = "b1", assignmentId = "assignment-old", slot = 5)
            advanceUntilIdle()
            vm.updateSlot(branchId = "b1", assignmentId = "assignment-old", slot = 5)
            advanceUntilIdle()

            assertEquals(2, harness.slotPaths.size)
            assertTrue(harness.slotPaths.all { it.endsWith("/assignment-old/slot") })
            assertIs<UiState.Error>(vm.slotUpdate.value)
        }

    @Test
    fun `roster failure can retry and successful retry replaces error`() =
        runTest(testScheduler) {
            val harness = RosterHarness(rosterFailure = true)
            val vm = AttendanceRosterViewModel(mockApiClient(harness.handler()))

            vm.load("b1")
            advanceUntilIdle()
            assertIs<UiState.Error>(vm.roster.value)

            harness.rosterFailure = false
            vm.load("b1")
            advanceUntilIdle()

            assertEquals(5, harness.rosterCount)
            assertIs<UiState.Success<List<com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse>>>(
                vm.roster.value,
            )
        }

    @Test
    fun `forbidden roster clears retained rows`() =
        runTest(testScheduler) {
            val harness = RosterHarness()
            val vm = AttendanceRosterViewModel(mockApiClient(harness.handler()))

            vm.load("b1")
            advanceUntilIdle()
            assertTrue(vm.freshestRoster.value != null)

            harness.rosterStatuses = ArrayDeque(listOf(HttpStatusCode.Forbidden))
            vm.load("b1")
            advanceUntilIdle()

            assertNull(vm.freshestRoster.value)
            assertEquals(UiState.Idle, vm.roster.value)
        }

    private class RosterHarness(
        var rosterStatuses: ArrayDeque<HttpStatusCode> = ArrayDeque(listOf(HttpStatusCode.OK)),
        var rosterFailure: Boolean = false,
        private val slotStatus: HttpStatusCode = HttpStatusCode.NoContent,
        private val swapStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ) {
        var rosterCount = 0
        val slotPaths = mutableListOf<String>()
        val slotBodies = mutableListOf<String>()
        val swapBodies = mutableListOf<String>()

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get &&
                        request.url.encodedPath == "/api/branches/b1/attendance/today" -> {
                        rosterCount++
                        if (rosterFailure) throw IOException("connection reset")
                        jsonResponse(
                            if (rosterStatuses.isEmpty()) HttpStatusCode.OK else rosterStatuses.removeFirst(),
                        )
                    }

                    request.method == HttpMethod.Patch -> {
                        slotPaths += request.url.encodedPath
                        slotBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(slotStatus, "")
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/branches/b1/slots/swap" -> {
                        swapBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(swapStatus, "")
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
                }
            }

        private suspend fun MockRequestHandleScope.jsonResponse(
            status: HttpStatusCode,
            body: String = ROSTER_JSON,
        ) = respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    private companion object {
        const val ROSTER_JSON =
            "[{\"assignmentId\":\"assignment-old\",\"userId\":\"user-me\",\"displayName\":\"Me\",\"slot\":1,\"present\":false}]"
    }
}
