package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
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
import io.ktor.http.isSuccess
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
            vm.setUserStatus("u2", UserStatus.INACTIVE)
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
    fun loadUsers_during_inflight_mutation_is_skipped() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            assertEquals(expected = 1, actual = harness.usersGetCount)

            // A reload mid-mutation would let the swap transform re-apply on the fresh list
            // (pass-2 HARD class: non-click triggers like LaunchedEffect refires on rotation/
            // re-entry bypass the Refresh-button gate). The guard skips the fetch entirely.
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            assertTrue(vm.inFlight.value.isNotEmpty())
            vm.loadUsers()
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.usersGetCount)
            assertTrue(vm.inFlight.value.isEmpty())
            // The in-place mutation still landed.
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = 2,
                actual =
                    state.data
                        .first { it.id == "u1" }
                        .assignments
                        .first { it.branchId == "b1" }
                        .slot,
            )
        }

    @Test
    fun loadUsers_skipped_keeps_existing_action_errors() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.deactivateStatus = HttpStatusCode.InternalServerError
            vm.setUserStatus("u2", UserStatus.INACTIVE)
            advanceUntilIdle()
            assertEquals(expected = "Deactivate failed: 500", actual = vm.actionErrors.value["deactivate:u2"])

            // A skipped load leaves the list untouched — the guard sits BEFORE the error clear
            // (pass-3 P2), so a completed failure's error survives a mid-mutation reload attempt
            // and still describes current state once the mutation lands.
            vm.swapSlots(branchId = "b1", userIdA = "u1", userIdB = "u2")
            vm.loadUsers()
            advanceUntilIdle()

            assertEquals(expected = "Deactivate failed: 500", actual = vm.actionErrors.value["deactivate:u2"])
            assertTrue(vm.inFlight.value.isEmpty())
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
    fun loadUsers_double_call_while_loading_fires_single_request() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            vm.loadUsers()
            advanceUntilIdle()

            // The synchronous Loading pre-set closes the same-frame double-fire (the #143
            // no-refire shape): the entry effect re-firing on rotation + a refresh tap in the
            // same frame must not stack two GETs.
            assertEquals(expected = 1, actual = harness.usersGetCount)
        }

    @Test
    fun loadUsers_failure_keeps_last_list_for_rendering() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.usersStatus = HttpStatusCode.BadRequest
            vm.loadUsers()
            advanceUntilIdle()

            // The freshest flow keeps the last successful list through an Error so the render
            // gate never swaps held rows for an ErrorCard (keep-last, #161 port / #162 unifier).
            assertIs<UiState.Error>(vm.users.value)
            val held = vm.freshestUsers.value
            assertNotNull(held)
            assertEquals(expected = listOf("u1", "u2", "u3"), actual = held.map { it.id })
        }

    @Test
    fun deactivate_after_failed_reload_mutates_held_list() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            harness.usersStatus = HttpStatusCode.BadRequest
            vm.loadUsers()
            advanceUntilIdle()
            assertIs<UiState.Error>(vm.users.value)

            // Row actions stay live over the mirror-rendered list: the PATCH succeeds and the
            // in-place update restores a Success list (the freshest truth for the row — the
            // NotificationViewModel currentUnreadList precedent).
            vm.setUserStatus("u1", UserStatus.INACTIVE)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = UserStatus.INACTIVE,
                actual =
                    state.data
                        .first { it.id == "u1" }
                        .status,
            )
            assertEquals(expected = listOf("u1", "u2", "u3"), actual = state.data.map { it.id })
        }

    @Test
    fun deactivate_while_load_in_flight_is_skipped() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            // The Loading pre-set is synchronous — the guard must skip the mutation (the
            // reload's pre-mutation snapshot would silently revert the PATCH — the pass-1
            // HARD interleave; the screen gate covers the affordance, this is the same-frame
            // belt).
            vm.setUserStatus("u1", UserStatus.INACTIVE)
            advanceUntilIdle()

            assertEquals(expected = 0, actual = harness.deactivateCount)
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = UserStatus.ACTIVE,
                actual =
                    state.data
                        .first { it.id == "u1" }
                        .status,
            )
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
            vm.setUserStatus("u2", UserStatus.INACTIVE)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val deactivated = state.data.first { it.id == "u2" }
            assertEquals(expected = UserStatus.INACTIVE, actual = deactivated.status)
            assertNotNull(deactivated.deactivatedAt)
            // Untouched rows stay untouched.
            assertEquals(
                expected = UserStatus.ACTIVE,
                actual =
                    state.data
                        .first { it.id == "u1" }
                        .status,
            )
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
            vm.setUserStatus("u2", UserStatus.INACTIVE)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = UserStatus.ACTIVE,
                actual =
                    state.data
                        .first { it.id == "u2" }
                        .status,
            )
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
            vm.setUserStatus("u2", UserStatus.INACTIVE)
            vm.setUserStatus("u2", UserStatus.INACTIVE)
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
            vm.setUserStatus("u2", UserStatus.INACTIVE)
            advanceUntilIdle()

            // ADR-0022 pessimistic contract: a dropped connection keeps the row, surfaces an
            // inline error, and re-enables the button (no frozen in-flight state).
            val error = vm.actionErrors.value["deactivate:u2"]
            assertNotNull(error)
            assertTrue(vm.inFlight.value.isEmpty())
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = UserStatus.ACTIVE,
                actual =
                    state.data
                        .first { it.id == "u2" }
                        .status,
            )
        }

    @Test
    fun reactivate_success_clears_deactivatedAt() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            vm.setUserStatus("u3", UserStatus.ACTIVE)
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val reactivated = state.data.first { it.id == "u3" }
            assertEquals(expected = UserStatus.ACTIVE, actual = reactivated.status)
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
            vm.setUserStatus("u3", UserStatus.ACTIVE)
            advanceUntilIdle()

            assertEquals(expected = "Reactivate failed: 400", actual = vm.actionErrors.value["reactivate:u3"])
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = UserStatus.INACTIVE,
                actual =
                    state.data
                        .first { it.id == "u3" }
                        .status,
            )
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
    fun swap_with_missing_user_is_noop() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            vm.loadUsers()
            advanceUntilIdle()
            // The stale-mirror shape: a swap for a user absent from the held list must not
            // write — the null-guard maps to no-write (the old `?: return` path). Note what
            // this test does NOT pin: a crash-class guard regression (e.g. first{} on the
            // missing user) would throw inside the transform, and the state-less handler's
            // onError no-op swallows it (the #168 launch) — unobservable through the public
            // surface, so no-crash is unpinnable here (the list-unchanged assert is the pin).
            vm.swapSlots(branchId = "b1", userIdA = "ghost", userIdB = "u2")
            advanceUntilIdle()

            assertEquals(
                expected = 1,
                actual = harness.swapBodies.size,
                "the swap request must have routed and 200'd — a reroute would pass the state asserts silently",
            )
            assertTrue(
                vm.actionErrors.value.isEmpty(),
                "a 200 transform must not surface an inline error",
            )
            val state = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            val u2 = state.data.first { it.id == "u2" }
            assertEquals(
                expected = 2,
                actual = u2.assignments.first { it.branchId == "b1" }.slot,
                "the list must be untouched by a swap for a missing user",
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
                user("u3", "Cal Lim", status = UserStatus.INACTIVE, assignments = listOf(assignment("b1", "Main", 1))),
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
    fun slotInputError_classifies_rejection_classes() {
        assertNull(slotInputError("1"))
        assertNull(slotInputError("32767"))
        assertNull(slotInputError(" 99 "))
        // Beyond SMALLINT — "too large", not "1 or greater" (the DTO slot is Short; backend
        // column SMALLINT). Regression-pinned: pass-1 classified 32768 wrong, pass-2 fixed the
        // ≤ Long.MAX window and broke > Long.MAX again; the digit-ness branch covers both.
        assertEquals(
            expected = "Slot number too large (max 32767)",
            actual = slotInputError("32768"),
        )
        assertEquals(
            expected = "Slot number too large (max 32767)",
            actual = slotInputError("9223372036854775808"),
        )
        assertEquals(
            expected = "Slot number too large (max 32767)",
            actual = slotInputError("99999999999999999999"),
        )
        // ≤ 0 and non-numeric — the backend's "Slot must be 1 or greater" (400).
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError("0"))
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError("-2"))
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError("abc"))
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError(""))
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError("1.5"))
        assertEquals(expected = "Slot must be 1 or greater", actual = slotInputError("12a"))
        // Kotlin's Short/Long parsing is digit-aware: non-ASCII numerals parse as their values
        // (pass-4's "Unicode-digit mislabel" SOFT was a false premise — toShortOrNull("٥") = 5,
        // pinned here). The digit-ness branch only fires on genuine overflow.
        assertNull(slotInputError("٥"))
        assertNull(slotInputError("１２"))
        assertEquals(
            expected = "Slot number too large (max 32767)",
            actual = slotInputError("٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩٩"),
        )
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

    // ---- #345 — create-user + role assignment ----

    @Test
    fun loadRoles_success_emits_list() =
        runTest(testScheduler) {
            val vm = UserViewModel(mockApiClient(UserHarness().handler()))

            vm.loadRoles()
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<RoleResponse>>>(vm.roles.value)
            assertEquals(expected = listOf("MANAGER", "CASHIER"), actual = state.data.map { it.name })
        }

    @Test
    fun mintInvite_success_appends_new_row_to_held_list() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()

            vm.mintInvite(InviteMintRequest(username = "new", email = "new@x.com", displayName = "New Staff"))
            advanceUntilIdle()

            val minted = assertIs<UiState.Success<InviteMintResponse>>(vm.mintInviteResult.value)
            assertEquals(expected = "u9", actual = minted.data.userId)
            assertEquals(expected = "single-use-code", actual = minted.data.inviteCode)
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("u1", "u2", "u3", "u9"), actual = users.data.map { it.id })
            assertTrue(
                "\"username\":\"new\"" in harness.mintBodies.single(),
                "the request must carry the form payload, got ${harness.mintBodies.single()}",
            )
        }

    @Test
    fun mintInvite_reinvite_of_listed_row_does_not_append_duplicate() =
        runTest(testScheduler) {
            // A re-invite targets an existing account (here u1): the code surfaces but the
            // held list must not grow a second row for the same id (#350).
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.reinvitedUserId = "u1"

            vm.mintInvite(InviteMintRequest(username = "ana", email = "a@x.com", displayName = "Ana Cruz"))
            advanceUntilIdle()

            assertIs<UiState.Success<InviteMintResponse>>(vm.mintInviteResult.value)
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("u1", "u2", "u3"), actual = users.data.map { it.id })
        }

    @Test
    fun mintInvite_duplicate_conflict_surfaces_backend_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.mintStatus = HttpStatusCode.Conflict
            harness.errorBody = """{"error":"Username already exists"}"""

            vm.mintInvite(InviteMintRequest("ana", "a@x.com", "Ana Cruz"))
            advanceUntilIdle()

            // The 409 body names the fix — surfaced verbatim (not the generic status text).
            val error = assertIs<UiState.Error>(vm.mintInviteResult.value)
            assertEquals(expected = "Username already exists", actual = error.message)
            // The held list is untouched by a failed mint.
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = 3, actual = users.data.size)
        }

    @Test
    fun mintInvite_forbidden_surfaces_status_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.mintStatus = HttpStatusCode.Forbidden

            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            advanceUntilIdle()

            // No error body on the route-filter 403 — the status fallback still renders.
            val error = assertIs<UiState.Error>(vm.mintInviteResult.value)
            assertTrue("403" in error.message)
        }

    @Test
    fun mintInvite_network_failure_surfaces_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.mintFailure = true

            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.mintInviteResult.value)
        }

    @Test
    fun mintInvite_double_tap_fires_single_request() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            // The synchronous Loading pre-set closes the same-frame double-fire — no two
            // accounts for one submit.
            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.mintCount)
        }

    @Test
    fun replaceRoles_success_updates_row_roles_in_place() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()

            vm.replaceRoles("u1", listOf("CASHIER", "MANAGER"))
            advanceUntilIdle()

            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("CASHIER", "MANAGER"), actual = users.data.first { it.id == "u1" }.roles)
            assertTrue(vm.inFlight.value.isEmpty())
            assertTrue(vm.actionErrors.value.isEmpty())
            val body = harness.replaceRolesBodies.single()
            assertTrue("CASHIER" in body && "MANAGER" in body, "full-replace body, got $body")
        }

    @Test
    fun replaceRoles_unknown_role_400_names_role_inline() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.replaceRolesStatus = HttpStatusCode.BadRequest
            harness.errorBody = """{"error":"Unknown role(s): BOSS"}"""

            vm.replaceRoles("u1", listOf("BOSS"))
            advanceUntilIdle()

            assertEquals(expected = "Unknown role(s): BOSS", actual = vm.actionErrors.value["roles:u1"])
            // The row keeps its previous bundle.
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("MANAGER"), actual = users.data.first { it.id == "u1" }.roles)
            assertTrue(vm.inFlight.value.isEmpty())
        }

    @Test
    fun replaceRoles_failure_keeps_roles_with_inline_status_error() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            harness.replaceRolesStatus = HttpStatusCode.InternalServerError

            vm.replaceRoles("u1", listOf("CASHIER"))
            advanceUntilIdle()

            assertEquals(expected = "Role update failed: 500", actual = vm.actionErrors.value["roles:u1"])
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = listOf("MANAGER"), actual = users.data.first { it.id == "u1" }.roles)
        }

    @Test
    fun loadRoles_double_call_while_loading_fires_single_request() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))

            // The synchronous Loading pre-set coalesces the same-frame entry-effect +
            // dialog-open double-fire (the loadUsers Guard-2 shape).
            vm.loadRoles()
            vm.loadRoles()
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.rolesGetCount)
        }

    @Test
    fun loadUsers_during_inflight_mint_is_skipped() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            advanceUntilIdle()
            assertEquals(expected = 1, actual = harness.usersGetCount)

            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            assertTrue(vm.inFlight.value.isNotEmpty())
            // A reload landing mid-mint would clobber the appended row with its pre-mint
            // snapshot — the tracked mint marker makes the reload skip (the swap variant of
            // this guard, now covering the invite mint too).
            vm.loadUsers()
            advanceUntilIdle()

            assertEquals(expected = 1, actual = harness.usersGetCount)
            assertTrue(vm.inFlight.value.isEmpty())
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(
                expected = listOf("u1", "u2", "u3", "u9"),
                actual = users.data.map { it.id },
            )
        }

    @Test
    fun mintInvite_skipped_while_users_reload_in_flight() =
        runTest(testScheduler) {
            val harness = UserHarness()
            val vm = UserViewModel(mockApiClient(harness.handler()))
            vm.loadUsers()
            // Reload still Loading (synchronous pre-set) — the same-frame submit must not
            // dispatch a POST whose append would be overwritten by the load's snapshot.
            vm.mintInvite(InviteMintRequest("new", "n@x.com", "New Staff"))
            advanceUntilIdle()

            assertEquals(expected = 0, actual = harness.mintCount)
            assertIs<UiState.Idle>(vm.mintInviteResult.value)
            // The reload itself completes normally.
            val users = assertIs<UiState.Success<List<UserSummaryResponse>>>(vm.users.value)
            assertEquals(expected = 3, actual = users.data.size)
        }

    @Test
    fun extractApiErrorMessage_parses_error_field_else_null() {
        val body = """{"error":"Username already exists"}"""
        assertEquals(expected = "Username already exists", actual = extractApiErrorMessage(body))
        assertNull(extractApiErrorMessage("""{"other":"x"}"""))
        assertNull(extractApiErrorMessage("not json"))
        assertNull(extractApiErrorMessage(null))
        assertNull(extractApiErrorMessage(""))
    }

    private fun user(
        id: String,
        displayName: String,
        status: UserStatus = UserStatus.ACTIVE,
        assignments: List<UserAssignmentResponse> = emptyList(),
        roles: List<String> = emptyList(),
    ): UserSummaryResponse =
        UserSummaryResponse(
            id = id,
            username = id,
            displayName = displayName,
            status = status,
            deactivatedAt = if (status == UserStatus.INACTIVE) "2026-08-01T02:00:00Z" else null,
            assignments = assignments,
            roles = roles,
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
        var rolesStatus: HttpStatusCode = HttpStatusCode.OK,
        var mintStatus: HttpStatusCode = HttpStatusCode.Created,
        var replaceRolesStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ) {
        var deactivateCount: Int = 0
        var usersGetCount: Int = 0
        var rolesGetCount: Int = 0
        var mintCount: Int = 0
        val swapBodies = mutableListOf<String>()
        val updateSlotBodies = mutableListOf<String>()
        val mintBodies = mutableListOf<String>()
        val replaceRolesBodies = mutableListOf<String>()

        // When true the deactivate handler throws — a network failure before any response.
        var deactivateFailure: Boolean = false

        // When true the invite-mint handler throws — a network failure before any response.
        var mintFailure: Boolean = false

        // When set, the mint response carries this userId (the re-invite shape: an already
        // listed account gets a fresh code instead of a new row).
        var reinvitedUserId: String? = null

        // Response body for create/role-replace failures (the backend's `{"error": ...}` shape).
        var errorBody: String? = null

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/users" -> {
                        usersGetCount++
                        jsonResponse(usersStatus, USERS_JSON)
                    }

                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/branches" -> {
                        jsonResponse(HttpStatusCode.OK, BRANCHES_JSON)
                    }

                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/roles" -> {
                        rolesGetCount++
                        jsonResponse(rolesStatus, ROLES_JSON)
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/invites" -> {
                        mintCount++
                        mintBodies += (request.body as? TextContent)?.text.orEmpty()
                        if (mintFailure) {
                            throw IOException("connection reset")
                        }
                        jsonResponse(
                            mintStatus,
                            if (mintStatus.isSuccess()) {
                                if (reinvitedUserId != null) {
                                    MINTED_JSON.replace("\"userId\":\"u9\"", "\"userId\":\"$reinvitedUserId\"")
                                } else {
                                    MINTED_JSON
                                }
                            } else {
                                errorBody.orEmpty()
                            },
                        )
                    }

                    request.method == HttpMethod.Put &&
                        request.url.encodedPath.endsWith("/roles") -> {
                        replaceRolesBodies += (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(replaceRolesStatus, errorBody.orEmpty())
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
                {"id":"u1","username":"ana","displayName":"Ana Cruz","status":"ACTIVE","deactivatedAt":null,"assignments":[{"branchId":"b1","branchName":"Main Branch","slot":1}],"roles":["MANAGER"]},
                {"id":"u2","username":"ben","displayName":"Ben Diaz","status":"ACTIVE","deactivatedAt":null,"assignments":[{"branchId":"b1","branchName":"Main Branch","slot":2},{"branchId":"b2","branchName":"Provincial","slot":1}]},
                {"id":"u3","username":"cal","displayName":"Cal Lim","status":"INACTIVE","deactivatedAt":"2026-08-01T02:00:00Z","assignments":[{"branchId":"b1","branchName":"Main Branch","slot":4}]}
            ]"""

        const val ROLES_JSON =
            """[
                {"name":"MANAGER","capabilities":["MANAGE_USERS"]},
                {"name":"CASHIER","capabilities":["SELL_PRODUCTS"]}
            ]"""

        const val MINTED_JSON =
            """{"userId":"u9","inviteCode":"single-use-code","expiresAt":"2026-08-29T00:00:00+00:00"}"""

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
