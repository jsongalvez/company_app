package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the User Management screen's state model (#135 build of the locked #106 D2-D5):
 * user/branch loads, pessimistic deactivate/reactivate/swap/update-slot (2xx mutates the
 * in-memory list in place; failure keeps the row + inline per-action error), the in-flight
 * double-tap guard, plus the pure D4 helpers (slot-order derivation, slot-number parsing,
 * client-side search).
 *
 * Uses the #93 handler-based MockEngine, `runTest(testScheduler)` + `StandardTestDispatcher`
 * main (same shape as AuditLogViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserManagementViewModelTest {
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
    fun loadUsers_success_emits_list() =
        runTest(testScheduler) {
            val vm = UserViewModel(mockApiClient(UserHarness().handler()))

            vm.loadUsers()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("u1", "u2", "u3"), actual = state.data.map { it.id })
        }

    @Test
    fun loadUsers_clears_stale_action_errors() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.deactivateStatus = HttpStatusCode.InternalServerError
            vm.deactivateUser("u2")
            advanceUntilIdle()
            assertEquals(expected = "Deactivate failed: 500", actual = vm.actionErrors.value["deactivate:u2"])

            // A reload replaces the list — the error described the pre-reload state and must not
            // linger beside fresh data (pass-1 P4 HARD: stale errors persisted for the VM's
            // lifetime, only cleared on same-key retry).
            vm.loadUsers()
            advanceUntilIdle()

            assertTrue(vm.actionErrors.value.isEmpty())
        }

    @Test
    fun loadUsers_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                UserViewModel(
                    mockApiClient(UserHarness(usersStatus = HttpStatusCode.InternalServerError).handler()),
                )

            vm.loadUsers()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.users.value)
        }

    @Test
    fun loadBranches_success_emits_list() =
        runTest(testScheduler) {
            val vm = UserViewModel(mockApiClient(UserHarness().handler()))

            vm.loadBranches()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<BranchResponse>>>(vm.branches.value)
            assertEquals(expected = listOf("b1", "b2"), actual = state.data.map { it.id })
        }

    @Test
    fun deactivate_success_updates_row_in_place() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.deactivateUser("u2")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val deactivated = state.data.first { it.id == "u2" }
            assertEquals(expected = USER_STATUS_INACTIVE, actual = deactivated.status)
            assertNotNull(deactivated.deactivatedAt)
            // Untouched rows stay untouched.
            assertEquals(expected = USER_STATUS_ACTIVE, actual = state.data.first { it.id == "u1" }.status)
            assertTrue(vm.inFlight.value.isEmpty())
            assertTrue(vm.actionErrors.value.isEmpty())
        }

    @Test
    fun deactivate_failure_keeps_row_with_inline_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.deactivateStatus = HttpStatusCode.InternalServerError
            vm.deactivateUser("u2")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = USER_STATUS_ACTIVE, actual = state.data.first { it.id == "u2" }.status)
            assertEquals(expected = "Deactivate failed: 500", actual = vm.actionErrors.value["deactivate:u2"])
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun deactivate_double_tap_fires_single_request() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.deactivateUser("u2")
            vm.deactivateUser("u2")
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.deactivateCount)
        }

    @Test
    fun mutation_network_failure_surfaces_inline_error_and_clears_inflight() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.deactivateFailure = true
            vm.deactivateUser("u2")
            advanceUntilIdle()

            // ADR-0022 pessimistic contract: a dropped connection keeps the row, surfaces an
            // inline error, and re-enables the button (no frozen in-flight state).
            val error = vm.actionErrors.value["deactivate:u2"]
            assertNotNull(error)
            assertTrue(vm.inFlight.value.isEmpty())
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = USER_STATUS_ACTIVE, actual = state.data.first { it.id == "u2" }.status)
        }

    @Test
    fun reactivate_success_clears_deactivatedAt() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.reactivateUser("u3")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val reactivated = state.data.first { it.id == "u3" }
            assertEquals(expected = USER_STATUS_ACTIVE, actual = reactivated.status)
            assertNull(reactivated.deactivatedAt)
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun reactivate_failure_inline_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.reactivateStatus = HttpStatusCode.BadRequest
            vm.reactivateUser("u3")
            advanceUntilIdle()

            assertEquals(expected = "Reactivate failed: 400", actual = vm.actionErrors.value["reactivate:u3"])
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = USER_STATUS_INACTIVE, actual = state.data.first { it.id == "u3" }.status)
        }

    @Test
    fun swap_success_exchanges_slots_in_place_only_at_branch() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val u1 = state.data.first { it.id == "u1" }
            val u2 = state.data.first { it.id == "u2" }
            assertEquals(expected = 2, actual = u1.assignments.first { it.branchId == "b1" }.slot)
            assertEquals(expected = 1, actual = u2.assignments.first { it.branchId == "b1" }.slot)
            // Other branches' assignments untouched.
            assertEquals(expected = 1, actual = u2.assignments.first { it.branchId == "b2" }.slot)
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun swap_forbidden_keeps_rows_with_inline_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.swapStatus = HttpStatusCode.Forbidden
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            advanceUntilIdle()

            assertEquals(expected = "Swap failed: 403", actual = vm.actionErrors.value["swap:b1:u1:u2"])
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = 1,
                actual =
                    state.data
                        .first { it.id == "u1" }
                        .assignments
                        .first {
                            it.branchId ==
                                "b1"
                        }.slot,
            )
            assertEquals(
                expected = 2,
                actual =
                    state.data
                        .first { it.id == "u2" }
                        .assignments
                        .first {
                            it.branchId ==
                                "b1"
                        }.slot,
            )
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun swap_sends_pairwise_request_body() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            advanceUntilIdle()

            val body = harness.swapBodies.single()
            assertTrue("u1" in body, "expected userIdA in body, got $body")
            assertTrue("u2" in body, "expected userIdB in body, got $body")
        }

    @Test
    fun swap_reversed_pair_same_frame_fires_single_request() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            // Row-B ▼ dispatches (u1,u2); row-C ▲ in the same frame dispatches (u2,u1) — the
            // normalized in-flight key must dedupe them (pass-1 P3 finding).
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            vm.swapSlots(branchId = "b1", userIdA = "u2", userIdB = "u1")
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.swapBodies.size)
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun updateSlot_success_updates_assignment_slot() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.updateSlot(branchId = "b1", userId = "u2", slot = 5)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val u2 = state.data.first { it.id == "u2" }
            assertEquals(expected = 5, actual = u2.assignments.first { it.branchId == "b1" }.slot)
            assertEquals(expected = 1, actual = u2.assignments.first { it.branchId == "b2" }.slot)
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun updateSlot_conflict_keeps_slot_with_inline_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.updateSlotStatus = HttpStatusCode.Conflict
            vm.updateSlot(branchId = "b1", userId = "u2", slot = 5)
            advanceUntilIdle()

            assertEquals(expected = "Slot update failed: 409", actual = vm.actionErrors.value["slot:b1:u2"])
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = 2,
                actual =
                    state.data
                        .first { it.id == "u2" }
                        .assignments
                        .first {
                            it.branchId ==
                                "b1"
                        }.slot,
            )
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun updateSlot_sends_slot_in_request_body() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.updateSlot(branchId = "b1", userId = "u2", slot = 5)
            advanceUntilIdle()

            assertTrue("5" in harness.updateSlotBodies.single())
        }

    @Test
    fun slotOrderForBranch_filters_branch_and_sorts_slot_then_display_name() {
        val users =
            listOf(
                user("u1", "Ana Cruz", assignments = listOf(assignment("b1", "Main", 3))),
                user("u2", "Ben Diaz", assignments = listOf(assignment("b1", "Main", 1), assignment("b2", "Prov", 1))),
                user("u3", "Cal Lim", assignments = listOf(assignment("b1", "Main", 1))),
                user("u4", "Dee Tan", assignments = listOf(assignment("b2", "Prov", 2))),
            )

        val rows = slotOrderForBranch(users, "b1")

        assertEquals(expected = listOf("u2", "u3", "u1"), actual = rows.map { it.userId })
        assertEquals(expected = listOf<Short>(1, 1, 3), actual = rows.map { it.slot })
        assertEquals(expected = false, actual = rows[0].isDeactivated)
    }

    @Test
    fun slotOrderForBranch_marks_deactivated_rows() {
        val users =
            listOf(
                user("u3", "Cal Lim", status = USER_STATUS_INACTIVE, assignments = listOf(assignment("b1", "Main", 1))),
            )

        val rows = slotOrderForBranch(users, "b1")

        assertTrue(rows.single().isDeactivated)
    }

    @Test
    fun slotOrderForBranch_skips_branchless_users() {
        val users = listOf(user("u4", "Dee Tan", assignments = emptyList()))

        assertTrue(slotOrderForBranch(users, "b1").isEmpty())
    }

    @Test
    fun parseSlotInput_accepts_positive_integers_only() {
        assertEquals(expected = 1, actual = parseSlotInput("1"))
        assertEquals(expected = 99, actual = parseSlotInput(" 99 "))
        assertNull(parseSlotInput("0"))
        assertNull(parseSlotInput("-2"))
        assertNull(parseSlotInput("abc"))
        assertNull(parseSlotInput(""))
        assertNull(parseSlotInput("1.5"))
    }

    @Test
    fun filterUsers_matches_display_name_or_username_case_insensitive() {
        val users =
            listOf(
                user("u1", "Ana Cruz"),
                user("u2", "Ben Diaz"),
                user("u3", "Cal Lim"),
            )

        assertEquals(expected = listOf("u1"), actual = filterUsers(users, "ana").map { it.id })
        assertEquals(expected = listOf("u3"), actual = filterUsers(users, "LIM").map { it.id })
        assertEquals(expected = listOf("u1", "u2"), actual = filterUsers(users, "n").map { it.id })
        assertEquals(expected = users, actual = filterUsers(users, "  "))
        assertEquals(expected = users, actual = filterUsers(users, ""))
    }

    private fun user(
        id: String,
        displayName: String,
        status: String = USER_STATUS_ACTIVE,
        assignments: List<UserAssignmentResponse> = emptyList(),
    ): UserSummaryResponse =
        UserSummaryResponse(
            id = id,
            username = id,
            displayName = displayName,
            status = status,
            deactivatedAt = if (status == USER_STATUS_INACTIVE) "2026-08-01T02:00:00Z" else null,
            assignments = assignments,
        )

    private fun assignment(
        branchId: String,
        branchName: String,
        slot: Short,
    ): UserAssignmentResponse =
        UserAssignmentResponse(
            branchId = branchId,
            branchName = branchName,
            slot = slot,
        )

    private class UserHarness(
        var usersStatus: HttpStatusCode = HttpStatusCode.OK,
        var deactivateStatus: HttpStatusCode = HttpStatusCode.OK,
        var reactivateStatus: HttpStatusCode = HttpStatusCode.OK,
        var swapStatus: HttpStatusCode = HttpStatusCode.OK,
        var updateSlotStatus: HttpStatusCode = HttpStatusCode.OK,
    ) {
        var deactivateCount: Int = 0
        val swapBodies = mutableListOf<String>()
        val updateSlotBodies = mutableListOf<String>()

        // When true the deactivate handler throws — a network failure before any response.
        var deactivateFailure: Boolean = false

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/users" -> {
                        jsonResponse(usersStatus, USERS_JSON)
                    }

                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/branches" -> {
                        jsonResponse(HttpStatusCode.OK, BRANCHES_JSON)
                    }

                    request.method == HttpMethod.Patch &&
                        request.url.encodedPath.endsWith("/deactivate") -> {
                        deactivateCount++
                        if (deactivateFailure) {
                            throw IOException("connection reset")
                        }
                        jsonResponse(deactivateStatus, "")
                    }

                    request.method == HttpMethod.Patch &&
                        request.url.encodedPath.endsWith("/reactivate") -> {
                        jsonResponse(reactivateStatus, "")
                    }

                    request.method == HttpMethod.Post &&
                        request.url.encodedPath.endsWith("/slots/swap") -> {
                        swapBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(swapStatus, "")
                    }

                    request.method == HttpMethod.Patch &&
                        request.url.encodedPath.endsWith("/slot") -> {
                        updateSlotBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(updateSlotStatus, "")
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
                }
            }
    }

    private companion object {
        const val USERS_JSON =
            """[
                {"id":"u1","username":"ana","displayName":"Ana Cruz","status":"ACTIVE","deactivatedAt":null,"assignments":[{"branchId":"b1","branchName":"Main Branch","slot":1}]},
                {"id":"u2","username":"ben","displayName":"Ben Diaz","status":"ACTIVE","deactivatedAt":null,"assignments":[{"branchId":"b1","branchName":"Main Branch","slot":2},{"branchId":"b2","branchName":"Provincial","slot":1}]},
                {"id":"u3","username":"cal","displayName":"Cal Lim","status":"INACTIVE","deactivatedAt":"2026-08-01T02:00:00Z","assignments":[{"branchId":"b1","branchName":"Main Branch","slot":4}]}
            ]"""

        const val BRANCHES_JSON =
            """[
                {"id":"b1","name":"Main Branch","branchType":"CLINIC"},
                {"id":"b2","name":"Provincial","branchType":"PROVINCIAL_TOUR"}
            ]"""
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
