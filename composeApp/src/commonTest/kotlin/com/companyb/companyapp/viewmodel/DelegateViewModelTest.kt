package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.ui.screen.eligibleDelegateUsers
import com.companyb.companyapp.ui.screen.medicalMissionBranches
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DelegateViewModelTest {
    private lateinit var scheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDelegates_success_emits_rows() =
        runTest(scheduler) {
            val harness = DelegateHarness()
            val viewModel = DelegateViewModel(mockApiClient(harness.handler()))

            viewModel.loadDelegates("b1")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<DelegateResponse>>>(viewModel.delegates.value)
            assertEquals(listOf("d1"), state.data.map { it.id })
            assertEquals(1, harness.listCount)
        }

    @Test
    fun force_refresh_reloads_selected_branch() =
        runTest(scheduler) {
            val harness = DelegateHarness()
            val viewModel = DelegateViewModel(mockApiClient(harness.handler()))

            viewModel.loadDelegates("b1")
            advanceUntilIdle()
            viewModel.loadDelegates("b1", force = true)
            advanceUntilIdle()

            assertEquals(2, harness.listCount)
            assertIs<UiState.Success<List<DelegateResponse>>>(viewModel.delegates.value)
        }

    @Test
    fun assign_success_emits_response_and_double_tap_is_coalesced() =
        runTest(scheduler) {
            val harness = DelegateHarness()
            val viewModel = DelegateViewModel(mockApiClient(harness.handler()))
            val request = AssignDelegateRequest(delegateId = "d1", targetUserId = "u1", branchId = "b1")

            viewModel.assignDelegate(request)
            viewModel.assignDelegate(request)
            advanceUntilIdle()

            assertIs<UiState.Success<DelegateResponse>>(viewModel.assignResult.value)
            assertEquals(1, harness.assignCount)
            assertTrue(harness.assignBody.contains("\"targetUserId\":\"u1\""))
        }

    @Test
    fun revoke_success_emits_unit_and_routes_to_delegate_id() =
        runTest(scheduler) {
            val harness = DelegateHarness()
            val viewModel = DelegateViewModel(mockApiClient(harness.handler()))

            viewModel.revokeDelegate("d1")
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(viewModel.revokeResult.value)
            assertEquals(1, harness.revokeCount)
        }

    @Test
    fun assign_error_uses_backend_message() =
        runTest(scheduler) {
            val harness = DelegateHarness(assignStatus = HttpStatusCode.BadRequest)
            val viewModel = DelegateViewModel(mockApiClient(harness.handler()))

            viewModel.assignDelegate(
                AssignDelegateRequest("d1", "u1", "b1"),
            )
            advanceUntilIdle()

            assertEquals(
                "Delegate target must be an active MANAGER",
                (viewModel.assignResult.value as UiState.Error).message,
            )
        }

    @Test
    fun branch_and_user_filters_keep_administration_scope() {
        val branches =
            medicalMissionBranches(
                listOf(
                    BranchResponse("clinic", "Clinic", BranchType.CLINIC),
                    BranchResponse("mission", "Mission", BranchType.MEDICAL_MISSION),
                ),
            )
        val users =
            listOf(
                user("manager", UserStatus.ACTIVE, listOf("MANAGER")),
                user("inactive", UserStatus.INACTIVE, listOf("MANAGER")),
                user("coordinator", UserStatus.ACTIVE, listOf("COORDINATOR")),
            )

        assertEquals(listOf("mission"), branches.map { it.id })
        assertEquals(listOf("manager"), eligibleDelegateUsers(users, emptyList()).map { it.id })
    }

    @Test
    fun revoked_delegate_is_eligible_again_but_active_delegate_is_not() {
        val manager = user("manager", UserStatus.ACTIVE, listOf("MANAGER"))
        val active = DelegateResponse("d1", "manager", "now", "admin", "b1")
        val revoked = active.copy(id = "d2", endedAt = "later")

        assertTrue(
            eligibleDelegateUsers(listOf(manager), listOf(revoked)).isNotEmpty(),
        )
        assertTrue(
            eligibleDelegateUsers(listOf(manager), listOf(active)).isEmpty(),
        )
    }

    private fun user(
        id: String,
        status: UserStatus,
        roles: List<String>,
    ) = UserSummaryResponse(id, id, id, status, roles = roles)

    private class DelegateHarness(
        private val assignStatus: HttpStatusCode = HttpStatusCode.Created,
    ) {
        var listCount = 0
        var assignCount = 0
        var revokeCount = 0
        var assignBody = ""

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/branches/b1/delegates" -> {
                        listCount++
                        jsonResponse(HttpStatusCode.OK, DELEGATE_JSON)
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/delegates" -> {
                        assignCount++
                        assignBody = (request.body as? TextContent)?.text.orEmpty()
                        jsonResponse(assignStatus, if (assignStatus.isSuccess()) DELEGATE_RESPONSE_JSON else ERROR_JSON)
                    }

                    request.method == HttpMethod.Delete && request.url.encodedPath == "/api/delegates/d1" -> {
                        revokeCount++
                        jsonResponse(HttpStatusCode.NoContent, "")
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
                }
            }
    }

    private companion object {
        const val DELEGATE_JSON =
            """[{"id":"d1","targetUser":"u1","assignedAt":"2026-08-31T00:00:00Z","assignedBy":"admin","branchId":"b1"}]"""
        const val DELEGATE_RESPONSE_JSON =
            """{"id":"d1","targetUser":"u1","assignedAt":"2026-08-31T00:00:00Z","assignedBy":"admin","branchId":"b1"}"""
        const val ERROR_JSON = """{"error":"Delegate target must be an active MANAGER"}"""
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
