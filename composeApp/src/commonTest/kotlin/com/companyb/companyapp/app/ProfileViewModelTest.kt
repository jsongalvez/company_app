package com.companyb.companyapp.app

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * #381 — the profile/self-service surface VM: three reads (`GET /api/me`, `/me/branches`,
 * `/me/capabilities`) plus the self slot edit (PATCH assignment slot; a 2xx re-fetches the
 * branch rows so the new slot shows immediately, and a 4xx surfaces the server's message).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
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

    private val meJson =
        """{"id":"u1","username":"dev","displayName":"Dev","status":"ACTIVE","createdAt":"2026-08-10T00:00:00+08:00"}"""

    private val capabilitiesJson =
        """
        [
          {"capabilityCode":"EDIT_BRANCH_DATA","contextType":"BRANCH","contextId":"b1","sourceType":"ROLE"}
        ]
        """.trimIndent()

    private fun branchesJson(slot: Short) =
        """
        [
          {"branchId":"b1","branchName":"Main Branch","branchType":"CLINIC","clockInStatus":"NOT_CLOCKED_IN","isRelief":false,"assignmentId":"a1","slot":$slot},
          {"branchId":"b2","branchName":"Relief Branch","branchType":"CLINIC","clockInStatus":"NOT_CLOCKED_IN","isRelief":true}
        ]
        """.trimIndent()

    /** Routes by URL; branch rows serve [slotRef]'s latest value so a post-PATCH refresh observes the new slot. */
    private fun profileHandler(
        slotRef: MutableList<Short>,
        patchStatus: HttpStatusCode = HttpStatusCode.NoContent,
        patchBody: String = "",
    ): MockRequestHandler =
        { request ->
            when {
                request.url.encodedPath.endsWith("/api/me") && request.method == HttpMethod.Get -> {
                    respondJson(meJson)
                }

                request.url.encodedPath.endsWith("/api/me/branches") && request.method == HttpMethod.Get -> {
                    respondJson(branchesJson(slotRef.last()))
                }

                request.url.encodedPath.endsWith("/api/me/capabilities") && request.method == HttpMethod.Get -> {
                    respondJson(capabilitiesJson)
                }

                request.url.encodedPath.endsWith("/slot") && request.method == HttpMethod.Patch -> {
                    if (patchStatus.isSuccess()) {
                        // Mirror the backend: a 2xx commits the requested slot for the next read.
                        val requested =
                            Json
                                .parseToJsonElement(request.body.toByteArray().decodeToString())
                                .jsonObject["slot"]
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?.toShort()
                        if (requested != null) slotRef[slotRef.lastIndex] = requested
                    }
                    respond(
                        content = ByteReadChannel(patchBody),
                        status = patchStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> {
                    respondError(HttpStatusCode.NotFound)
                }
            }
        }

    @Test
    fun loadAll_success_emits_all_three_sections() =
        runTest(testScheduler) {
            val vm = ProfileViewModel(mockApiClient(profileHandler(mutableListOf(2))))

            vm.loadAll()
            advanceUntilIdle()

            val meState = assertIs<UiState.Success<com.companyb.companyapp.contracts.identity.MeResponse>>(vm.me.value)
            assertEquals("Dev", meState.data.displayName)
            val branchState = assertIs<UiState.Success<List<MeBranchResponse>>>(vm.branches.value)
            assertEquals(listOf("b1", "b2"), branchState.data.map { it.branchId })
            assertEquals(2.toShort(), branchState.data.first { it.branchId == "b1" }.slot)
            assertIs<UiState.Success<List<UserCapabilityResponse>>>(vm.capabilities.value)
        }

    @Test
    fun loadAll_failure_on_me_leg_surfaces_error_with_other_sections_intact() =
        runTest(testScheduler) {
            // The /api/me leg alone 500s: the handler falls through to respondError for it.
            val vm =
                ProfileViewModel(
                    mockApiClient({ request ->
                        if (request.url.encodedPath.endsWith("/api/me") &&
                            request.method == HttpMethod.Get
                        ) {
                            respondError(HttpStatusCode.InternalServerError)
                        } else {
                            profileHandler(mutableListOf(1))(request)
                        }
                    }),
                )

            vm.loadAll()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.me.value)
            assertIs<UiState.Success<List<MeBranchResponse>>>(vm.branches.value)
            assertIs<UiState.Success<List<UserCapabilityResponse>>>(vm.capabilities.value)
        }

    @Test
    fun updateSlot_success_refreshes_branch_rows_immediately() =
        runTest(testScheduler) {
            val slots = mutableListOf<Short>(2)
            val vm = ProfileViewModel(mockApiClient(profileHandler(slots)))
            vm.loadAll()
            advanceUntilIdle()

            vm.updateSlot(branchId = "b1", assignmentId = "a1", slot = 5)
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(vm.slotUpdate.value)
            val branchState = assertIs<UiState.Success<List<MeBranchResponse>>>(vm.branches.value)
            assertEquals(5.toShort(), branchState.data.first { it.branchId == "b1" }.slot)
        }

    @Test
    fun updateSlot_4xx_surfaces_server_message_inline() =
        runTest(testScheduler) {
            val slots = mutableListOf<Short>(2)
            val vm =
                ProfileViewModel(
                    mockApiClient(
                        profileHandler(
                            slots,
                            patchStatus = HttpStatusCode.BadRequest,
                            patchBody = """{"error":"Slot must be 1 or greater"}""",
                        ),
                    ),
                )
            vm.loadAll()
            advanceUntilIdle()

            vm.updateSlot(branchId = "b1", assignmentId = "a1", slot = 0)
            advanceUntilIdle()

            val error = assertIs<UiState.Error>(vm.slotUpdate.value)
            assertEquals("Slot must be 1 or greater", error.message)
        }
}

/** JSON 200 responder; extension because ktor's mock `respond` lives on the handle scope. */
private suspend fun MockRequestHandleScope.respondJson(json: String): HttpResponseData =
    respond(
        content = ByteReadChannel(json),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
