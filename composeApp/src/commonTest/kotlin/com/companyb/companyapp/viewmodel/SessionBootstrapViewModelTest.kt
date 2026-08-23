package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.SessionState
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #94-grad — the shared session-bootstrap implementation (launch validation + fresh login):
 * GET /api/me → GET /api/me/capabilities → atomic bootstrap publication with the FULL row list
 * (#156; the pre-clock-in two-slice filter is gone — branch-scoped resolution fails closed until
 * clock-in). A 401 is deliberately NOT UiState.Error (ApiClient's global onUnauthorized clears
 * the token; the splash derives from token presence); network errors ARE Error (the splash keeps
 * the token and offers Retry — #94 Q3b(ii)).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionBootstrapViewModelTest {
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

    private val meJson =
        """{"id":"u1","username":"dev","displayName":"Dev","status":"ACTIVE","createdAt":"2026-08-10T00:00:00+08:00"}"""

    private val capabilitiesJson =
        """
        [
          {"capabilityCode":"MANAGE_USERS","contextType":"GLOBAL","contextId":"00000000-0000-0000-0000-000000000000","sourceType":"ROLE"},
          {"capabilityCode":"SUBMIT_REMITTANCE","contextType":"BRANCH","contextId":"b1","sourceType":"MANUAL_OVERRIDE"},
          {"capabilityCode":"EDIT_BRANCH_DATA","contextType":"BRANCH","contextId":"b2","sourceType":"MANUAL_OVERRIDE"}
        ]
        """.trimIndent()

    private fun bootstrapHandler(
        meStatus: HttpStatusCode = HttpStatusCode.OK,
        capsStatus: HttpStatusCode = HttpStatusCode.OK,
        meBody: String = meJson,
        capsBody: String = capabilitiesJson,
    ): MockRequestHandler =
        { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/me" -> {
                    respond(
                        content = ByteReadChannel(meBody),
                        status = meStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                path == "/api/me/capabilities" -> {
                    respond(
                        content = ByteReadChannel(capsBody),
                        status = capsStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> {
                    respond("", HttpStatusCode.NotFound)
                }
            }
        }

    @Test
    fun validateSession_success_populates_user_and_full_capability_list() =
        runTest(testScheduler) {
            val vm = SessionBootstrapViewModel(mockApiClient(bootstrapHandler()))

            vm.validateSession()
            advanceUntilIdle()

            assertIs<UiState.Success<Unit>>(vm.validationState.value)
            val user = assertIs<MeResponse>(SessionState.currentUser.value)
            assertEquals("u1", user.id)
            // #156 — the full row list is stored (contexts preserved); the clock-in refetch
            // (ADR-0021 timing) refreshes it wholesale.
            val caps = SessionState.capabilities.value
            assertEquals(3, caps.size)
            assertEquals("MANAGE_USERS", caps[0].capabilityCode)
            assertEquals("GLOBAL", caps[0].contextType.name)
            assertEquals("SUBMIT_REMITTANCE", caps[1].capabilityCode)
            assertEquals("BRANCH", caps[1].contextType.name)
            assertEquals("EDIT_BRANCH_DATA", caps[2].capabilityCode)
            assertEquals("BRANCH", caps[2].contextType.name)
        }

    @Test
    fun validateSession_401_is_silent_no_error_no_user() =
        runTest(testScheduler) {
            val vm =
                SessionBootstrapViewModel(
                    mockApiClient(bootstrapHandler(meStatus = HttpStatusCode.Unauthorized)),
                )

            vm.validateSession()
            advanceUntilIdle()

            // Not Error: the global onUnauthorized handler clears the token, which is what
            // transitions the splash → Login. Idle (not a lying stuck-Loading) so a
            // re-composed LoginScreen's form isn't left disabled.
            assertEquals(UiState.Idle, vm.validationState.value)
            assertNull(SessionState.currentUser.value)
            assertEquals(emptyList<UserCapabilityResponse>(), SessionState.capabilities.value)
        }

    @Test
    fun validateSession_network_error_is_error() =
        runTest(testScheduler) {
            val vm =
                SessionBootstrapViewModel(
                    mockApiClient(
                        { _: Any ->
                            throw java.io.IOException("connection refused")
                        },
                    ),
                )

            vm.validateSession()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.validationState.value)
            assertNull(SessionState.currentUser.value)
        }

    @Test
    fun validateSession_capabilities_401_is_silent_like_me_leg() =
        runTest(testScheduler) {
            val vm =
                SessionBootstrapViewModel(
                    mockApiClient(
                        bootstrapHandler(capsStatus = HttpStatusCode.Unauthorized),
                    ),
                )

            SessionState.setUser(
                MeResponse(
                    id = "stale",
                    username = "stale",
                    displayName = "Stale",
                    status = com.companyb.companyapp.domain.UserStatus.ACTIVE,
                    createdAt = "2026-08-10T00:00:00+08:00",
                ),
            )

            vm.validateSession()
            advanceUntilIdle()

            // The capabilities-leg 401 is the same session-401 class as the me-leg — the
            // global onUnauthorized handler clears the token (silent at launch, notice +
            // navigate mid-session). NOT UiState.Error: an auth failure must not read as a
            // connection problem (and never renders the network copy on the Login screen).
            assertFalse(vm.validationState.value is UiState.Error)
            assertEquals(UiState.Idle, vm.validationState.value)
            assertNull(SessionState.currentUser.value)
        }

    @Test
    fun validateSession_capabilities_500_is_error() =
        runTest(testScheduler) {
            val vm =
                SessionBootstrapViewModel(
                    mockApiClient(
                        bootstrapHandler(capsStatus = HttpStatusCode.InternalServerError),
                    ),
                )

            vm.validateSession()
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.validationState.value)
            assertNull(SessionState.currentUser.value)
        }

    @Test
    fun validateSession_ignores_second_call_while_loading() =
        runTest(testScheduler) {
            var meCalls = 0
            val handler: MockRequestHandler = { request ->
                if (request.url.encodedPath == "/api/me") {
                    meCalls++
                    respond(
                        content = ByteReadChannel(meJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                } else {
                    respond(
                        content = ByteReadChannel(capabilitiesJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            val vm = SessionBootstrapViewModel(mockApiClient(handler))

            vm.validateSession()
            vm.validateSession()
            vm.validateSession()
            advanceUntilIdle()

            assertEquals(1, meCalls)
            assertIs<UiState.Success<Unit>>(vm.validationState.value)
        }

    @Test
    fun cancel_cancels_in_flight_validation_before_global_state_write() =
        runTest(testScheduler) {
            var meResponded = false
            val capabilitiesStarted = CompletableDeferred<Unit>()
            val handler: MockRequestHandler = { request ->
                if (!meResponded && request.url.encodedPath == "/api/me") {
                    meResponded = true
                    respond(
                        content = ByteReadChannel(meJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                } else {
                    capabilitiesStarted.complete(Unit)
                    awaitCancellation()
                }
            }
            val vm = SessionBootstrapViewModel(mockApiClient(handler))

            val job = vm.validateSession()
            runCurrent()

            assertTrue(capabilitiesStarted.isCompleted)
            vm.cancelValidation()
            advanceUntilIdle()

            assertFalse(job.isActive)
            assertNull(SessionState.currentUser.value)
            assertEquals(emptyList<UserCapabilityResponse>(), SessionState.capabilities.value)
        }
}
