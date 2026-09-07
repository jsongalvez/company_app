package com.companyb.companyapp.workforce.branch

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BranchViewModelTest {
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
    fun createBranch_success_sends_request_and_emits_branch() =
        runTest(testScheduler) {
            val harness = BranchHarness()
            val vm = BranchViewModel(mockApiClient(harness.handler()))

            vm.createBranch(CreateBranchRequest("b1", "Mission", BranchType.MEDICAL_MISSION))
            advanceUntilIdle()

            val state = assertIs<UiState.Success<BranchResponse>>(vm.createBranchState.value)
            assertEquals("b1", state.data.id)
            assertEquals(1, harness.branchCount)
            assertTrue("Mission" in harness.branchBodies.single())
            assertTrue("MEDICAL_MISSION" in harness.branchBodies.single())
        }

    @Test
    fun createBranch_double_tap_sends_one_request() =
        runTest(testScheduler) {
            val harness = BranchHarness()
            val vm = BranchViewModel(mockApiClient(harness.handler()))
            val request = CreateBranchRequest("b1", "Mission", BranchType.CLINIC)

            vm.createBranch(request)
            vm.createBranch(request)
            advanceUntilIdle()

            assertEquals(1, harness.branchCount)
        }

    @Test
    fun createBranch_failure_surfaces_backend_error_and_can_retry() =
        runTest(testScheduler) {
            val harness = BranchHarness(branchStatus = HttpStatusCode.BadRequest)
            harness.branchBody = """{"error":"Branch name already exists"}"""
            val vm = BranchViewModel(mockApiClient(harness.handler()))
            val request = CreateBranchRequest("b1", "Mission", BranchType.CLINIC)

            vm.createBranch(request)
            advanceUntilIdle()
            val failure = assertIs<UiState.Error>(vm.createBranchState.value)
            assertEquals("Branch name already exists", failure.message)

            harness.branchStatus = HttpStatusCode.Created
            harness.branchBody = BRANCH_JSON
            vm.createBranch(request)
            advanceUntilIdle()

            assertIs<UiState.Success<BranchResponse>>(vm.createBranchState.value)
            assertEquals(2, harness.branchCount)
        }

    @Test
    fun createAssignment_success_sends_slot_and_emits_assignment() =
        runTest(testScheduler) {
            val harness = BranchHarness()
            val vm = BranchViewModel(mockApiClient(harness.handler()))

            vm.createAssignment(
                branchId = "b1",
                request = CreateAssignmentRequest("a1", "u1", 3),
            )
            advanceUntilIdle()

            assertIs<UiState.Success<AssignmentResponse>>(vm.assignmentResult.value)
            assertEquals(1, harness.assignmentCount)
            assertTrue("\"userId\":\"u1\"" in harness.assignmentBodies.single())
            assertTrue("\"slot\":3" in harness.assignmentBodies.single())
        }

    @Test
    fun deleteAssignment_success_routes_to_assignment_endpoint() =
        runTest(testScheduler) {
            val harness = BranchHarness()
            val vm = BranchViewModel(mockApiClient(harness.handler()))

            vm.deleteAssignment(branchId = "b1", assignmentId = "a1")
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(vm.deleteAssignmentState.value)
            assertEquals(1, harness.deleteCount)
        }

    @Test
    fun deleteAssignment_failure_surfaces_backend_error() =
        runTest(testScheduler) {
            val harness = BranchHarness(deleteStatus = HttpStatusCode.NotFound)
            harness.deleteBody = """{"error":"Active assignment not found"}"""
            val vm = BranchViewModel(mockApiClient(harness.handler()))

            vm.deleteAssignment(branchId = "b1", assignmentId = "a1")
            advanceUntilIdle()

            val failure = assertIs<UiState.Error>(vm.deleteAssignmentState.value)
            assertEquals("Active assignment not found", failure.message)
        }

    @Test
    fun updateSlot_success_accepts_no_content_response() =
        runTest(testScheduler) {
            val harness = BranchHarness()
            val vm = BranchViewModel(mockApiClient(harness.handler()))

            vm.updateSlot("b1", "a1", UpdateSlotRequest(slot = 4))
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(vm.slotUpdate.value)
            assertEquals(1, harness.updateSlotCount)
        }

    @Test
    fun assignment_failure_emits_error_and_can_retry() =
        runTest(testScheduler) {
            val harness = BranchHarness(assignmentStatus = HttpStatusCode.BadRequest)
            harness.assignmentBody = """{"error":"User already has an active assignment at this branch"}"""
            val vm = BranchViewModel(mockApiClient(harness.handler()))
            val request = CreateAssignmentRequest("a1", "u1", 3)

            vm.createAssignment("b1", request)
            advanceUntilIdle()
            val failure = assertIs<UiState.Error>(vm.assignmentResult.value)
            assertEquals("User already has an active assignment at this branch", failure.message)

            harness.assignmentStatus = HttpStatusCode.Created
            harness.assignmentBody = ASSIGNMENT_JSON
            vm.createAssignment("b1", request)
            advanceUntilIdle()

            assertIs<UiState.Success<AssignmentResponse>>(vm.assignmentResult.value)
            assertEquals(2, harness.assignmentCount)
        }

    private class BranchHarness(
        var branchStatus: HttpStatusCode = HttpStatusCode.Created,
        var assignmentStatus: HttpStatusCode = HttpStatusCode.Created,
        var deleteStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ) {
        var branchCount = 0
        var assignmentCount = 0
        var deleteCount = 0
        var updateSlotCount = 0
        var branchBody = BRANCH_JSON
        var assignmentBody = ASSIGNMENT_JSON
        var deleteBody = ""
        val branchBodies = mutableListOf<String>()
        val assignmentBodies = mutableListOf<String>()

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/branches" -> {
                        branchCount++
                        branchBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(branchStatus, branchBody)
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/branches/b1/assignments" -> {
                        assignmentCount++
                        assignmentBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(assignmentStatus, assignmentBody)
                    }

                    request.method == HttpMethod.Delete &&
                        request.url.encodedPath == "/api/branches/b1/assignments/a1" -> {
                        deleteCount++
                        jsonResponse(deleteStatus, deleteBody)
                    }

                    request.method == HttpMethod.Patch &&
                        request.url.encodedPath == "/api/branches/b1/assignments/a1/slot" -> {
                        updateSlotCount++
                        jsonResponse(HttpStatusCode.NoContent, "")
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
                }
            }
    }

    private companion object {
        const val BRANCH_JSON = """{"id":"b1","name":"Mission","branchType":"MEDICAL_MISSION"}"""
        const val ASSIGNMENT_JSON =
            """{"id":"a1","userId":"u1","branchId":"b1","slot":3,"assignedBy":"admin","assignedAt":"2026-08-31T00:00:00Z"}"""
    }
}

private fun MockRequestHandleScope.jsonResponse(
    status: HttpStatusCode,
    body: String,
) = respond(
    content = ByteReadChannel(body),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
