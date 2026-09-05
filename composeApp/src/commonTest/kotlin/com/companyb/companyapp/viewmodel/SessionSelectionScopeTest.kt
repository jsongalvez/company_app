package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.SessionVoidResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * #486 — the desktop pane keys one VM per selection (`viewModel(key = session.id)`), so
 * every new selection starts from a fresh scope: raw roster/member state is Idle (nothing
 * stale for `mergeRosterNames` to map) and no sticky result replays into the new pane.
 * A revisit reuses the cached keyed VM — retained terminals refresh on re-entry (#489)
 * instead of replaying silently. Within a scope the #382 stamp/fallback guard keeps
 * latest-wins: a superseded roster GET never deserializes over the newer commit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSelectionScopeTest {
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
    fun freshScope_allFlowsStartIdle() =
        runTest(testScheduler) {
            val vm =
                SessionViewModel(
                    mockApiClient { request ->
                        error("fresh scope dispatches nothing: ${request.method} ${request.url.encodedPath}")
                    },
                )
            runCurrent()

            assertIs<UiState.Idle>(vm.practitioners.value)
            assertIs<UiState.Idle>(vm.branchMembers.value)
            assertIs<UiState.Idle>(vm.practitionerResult.value)
            assertIs<UiState.Idle>(vm.concernResult.value)
        }

    @Test
    fun roster_supersededLoad_dropsStaleBody() =
        runTest(testScheduler) {
            // Gate the first load, land a newer load first, then release the stale one with
            // divergent rows. Without the guard the stale body would last-writer-win.
            val staleGate = CompletableDeferred<Unit>()
            var rosterGets = 0
            val vm =
                SessionViewModel(
                    mockApiClient { request ->
                        if (request.method == HttpMethod.Get &&
                            request.url.encodedPath == "/api/sessions/s1/practitioners"
                        ) {
                            rosterGets++
                            if (rosterGets == 1) {
                                staleGate.await()
                                jsonRespond(status = HttpStatusCode.OK, body = OLD_ROSTER_JSON)
                            } else {
                                jsonRespond(status = HttpStatusCode.OK, body = NEW_ROSTER_JSON)
                            }
                        } else {
                            error("unexpected request: ${request.method} ${request.url.encodedPath}")
                        }
                    },
                )

            vm.loadSessionPractitioners("s1")
            runCurrent()
            vm.loadSessionPractitioners("s1")
            advanceUntilIdle()

            val landed =
                assertIs<UiState.Success<List<SessionPractitionerResponse>>>(vm.practitioners.value)
            assertEquals(listOf("p-new"), landed.data.map { it.practitionerId })

            staleGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, rosterGets)
            val committed =
                assertIs<UiState.Success<List<SessionPractitionerResponse>>>(vm.practitioners.value)
            assertEquals(listOf("p-new"), committed.data.map { it.practitionerId })
            assertTrue(committed.data.none { it.practitionerId == "p-old" })
        }

    @Test
    fun voidUnvoid_retainedTerminalUntilConsumed() =
        runTest(testScheduler) {
            // #489 — the revisit-refresh contract: a terminal void/unvoid landing is
            // retained in the keyed VM until the pane drains it, so a re-entered
            // selection still observes (and refreshes on) the retained terminal.
            val vm =
                SessionViewModel(
                    mockApiClient { request ->
                        when (request.url.encodedPath) {
                            "/api/sessions/s1/void" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = VOID_JSON)
                            }

                            "/api/sessions/s1/unvoid" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = VOID_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )
            runCurrent()

            vm.voidSession("s1", VoidSessionRequest(id = "cmd-1", voidReason = "no-show"))
            advanceUntilIdle()
            assertIs<UiState.Success<SessionVoidResponse>>(vm.voidResult.value)
            vm.consumeVoidResult()
            assertIs<UiState.Idle>(vm.voidResult.value)

            vm.unvoidSession("s1", UnvoidSessionRequest(unvoidedReason = "returned"))
            advanceUntilIdle()
            assertIs<UiState.Success<SessionVoidResponse>>(vm.unvoidResult.value)
            vm.consumeUnvoidResult()
            assertIs<UiState.Idle>(vm.unvoidResult.value)
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
        const val OLD_ROSTER_JSON =
            """[{"id":"rp-old","sessionId":"s1","practitionerId":"p-old","remarks":"old","slotAtTime":2}]"""
        const val NEW_ROSTER_JSON =
            """[{"id":"rp-new","sessionId":"s1","practitionerId":"p-new","remarks":null,"slotAtTime":1}]"""
        const val VOID_JSON =
            """{"id":"v1","sessionId":"s1","voidedAt":"2026-09-05T10:00:00+08:00",""" +
                """"voidedBy":"u1","voidReason":"no-show"}"""
    }
}
