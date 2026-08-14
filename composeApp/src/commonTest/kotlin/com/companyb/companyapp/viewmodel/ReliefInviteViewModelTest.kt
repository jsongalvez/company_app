package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * #160 — the ReliefInviteViewModel flows: received list (keep-last + no-refire), accept/
 * decline (row leaves + badge decrement), inviter side (search cancel, send → sent reload,
 * retract). Entry-scoped VM — no poll loop, so plain runTest drains are safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReliefInviteViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        NotificationState.clear()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        NotificationState.clear()
    }

    private fun inviteJson(
        id: String,
        status: String,
        date: String = "2026-08-16",
        inviteeName: String = "Invitee",
    ): String =
        """{"id":"$id","branchId":"b1","branchName":"Branch A","branchDayId":"bd1",""" +
            """"date":"$date","invitedBy":"inviter","inviterName":"Inviter","invitee":"invitee",""" +
            """"inviteeName":"$inviteeName","status":"$status","createdAt":"2026-08-01T00:00:00Z"}"""

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.ok(body: String) =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    @Test
    fun `loadReceived_commits_success_and_mirrors_keep_last`() =
        runTest(testScheduler) {
            var receivedCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> {
                                receivedCalls++
                                ok("[${inviteJson("i1", "PENDING")}]")
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadReceived()
            runCurrent()
            val state = vm.received.value
            assertIs<UiState.Success<List<ReliefInviteResponse>>>(state)
            assertEquals(1, state.data.size)
            assertEquals(1, vm.lastReceived.value!!.size)
            assertEquals(1, receivedCalls)

            // Re-entry refires (no Loading guard once Success) but keep-last mirrors every Success.
            vm.loadReceived()
            runCurrent()
            assertEquals(2, receivedCalls)
            assertEquals(1, vm.lastReceived.value!!.size)
        }

    @Test
    fun `accept_removes_row_and_decrements_the_badge`() =
        runTest(testScheduler) {
            NotificationState.setInviteCount(1)
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> ok("[${inviteJson("i1", "PENDING")}]")
                            it.url.encodedPath == "/api/relief-invites/i1/accept" -> ok(inviteJson("i1", "ACCEPTED"))
                            else -> ok("[]")
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadReceived()
            runCurrent()

            vm.acceptInvite("i1")
            runCurrent()
            assertIs<UiState.Success<Unit>>(vm.acceptResult.value)
            assertEquals(0, vm.lastReceived.value!!.size, "resolved rows leave the list")
            assertEquals(0, NotificationState.inviteCount.value)
        }

    @Test
    fun `decline_removes_row_and_decrements_the_badge`() =
        runTest(testScheduler) {
            NotificationState.setInviteCount(2)
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> {
                                ok("[${inviteJson("i1", "PENDING")}, ${inviteJson("i2", "PENDING")}]")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/decline" -> {
                                ok(inviteJson("i1", "DECLINED"))
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadReceived()
            runCurrent()

            vm.declineInvite("i1")
            runCurrent()
            assertEquals(1, vm.lastReceived.value!!.size)
            assertEquals(
                "i2",
                vm.lastReceived.value!!
                    .single()
                    .id,
            )
            assertEquals(1, NotificationState.inviteCount.value)
        }

    @Test
    fun `accept_failure_surfaces_error_and_keeps_the_row`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> {
                                ok("[${inviteJson("i1", "PENDING")}]")
                            }

                            else -> {
                                respond(
                                    content = ByteReadChannel("""{"error":"conflict"}"""),
                                    status = HttpStatusCode.Conflict,
                                    headers =
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        ),
                                )
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadReceived()
            runCurrent()

            vm.acceptInvite("i1")
            runCurrent()
            assertIs<UiState.Error>(vm.acceptResult.value)
            assertEquals(1, vm.lastReceived.value!!.size, "a failed accept keeps the row")
        }

    @Test
    fun `newer_search_cancels_the_in_flight_one`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-candidates" -> {
                                val query = it.url.parameters["q"].orEmpty()
                                if (query == "al") {
                                    ok("""[{"id":"u1","username":"alice","displayName":"Alice"}]""")
                                } else {
                                    ok("[]")
                                }
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.searchCandidates("b1", "al", "2026-08-16")
            runCurrent()
            val first = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(first)
            assertEquals(1, first.data.size)

            vm.searchCandidates("b1", "bo", "2026-08-16")
            runCurrent()
            val second = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(second)
            assertEquals(0, second.data.size, "the newer query wins")
        }

    @Test
    fun `send_invite_reloads_sent_list_and_drops_the_candidate_locally`() =
        runTest(testScheduler) {
            var sentCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-candidates" -> {
                                ok("""[{"id":"u1","username":"alice","displayName":"Alice"}]""")
                            }

                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                sentCalls++
                                ok("""[${inviteJson("i1", "PENDING", inviteeName = "Alice")}]""")
                            }

                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "POST" -> {
                                ok(inviteJson("i1", "PENDING", inviteeName = "Alice"))
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.searchCandidates("b1", "", "2026-08-16")
            runCurrent()

            vm.sendInvite("b1", "u1", "2026-08-16")
            runCurrent()
            assertIs<UiState.Success<Unit>>(vm.createResult.value)
            val candidates = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(candidates)
            assertEquals(0, candidates.data.size, "invited user leaves the results")
            assertEquals(1, sentCalls, "send reloads the sent list")
            assertEquals(1, vm.lastSent.value!!.size)
        }

    @Test
    fun `retract_invite_reloads_the_sent_list`() =
        runTest(testScheduler) {
            var sentCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                sentCalls++
                                ok("""[${inviteJson("i1", "RETRACTED", inviteeName = "Alice")}]""")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/retract" -> {
                                ok(inviteJson("i1", "RETRACTED", inviteeName = "Alice"))
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadSent("b1")
            runCurrent()
            assertEquals(1, vm.lastSent.value!!.size)
            assertEquals(1, sentCalls)

            vm.retractInvite("i1", "b1")
            runCurrent()
            assertIs<UiState.Success<Unit>>(vm.retractResult.value)
            assertEquals(2, sentCalls)
        }

    @Test
    fun `received_keep_last_survives_a_reload_error`() =
        runTest(testScheduler) {
            var failNext = false
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> {
                                if (failNext) {
                                    // 400 — NOT a 5xx, so ktor's HttpRequestRetry plugin does not
                                    // hold the request in its backoff delay.
                                    respond(
                                        content = ByteReadChannel("""{"error":"bad request"}"""),
                                        status = HttpStatusCode.BadRequest,
                                        headers =
                                            headersOf(
                                                HttpHeaders.ContentType,
                                                ContentType.Application.Json.toString(),
                                            ),
                                    )
                                } else {
                                    ok("[${inviteJson("i1", "PENDING")}]")
                                }
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadReceived()
            runCurrent()
            assertEquals(1, vm.lastReceived.value!!.size)

            failNext = true
            vm.loadReceived()
            runCurrent()
            assertIs<UiState.Error>(vm.received.value)
            assertEquals(1, vm.lastReceived.value!!.size, "keep-last survives the reload error")
        }

    @Test
    fun `stale branch load never commits under the new branch panel`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                // Virtualized delay: b1's load is still in flight when b2's panel opens.
                                withContext(StandardTestDispatcher(testScheduler)) { kotlinx.coroutines.delay(10_000) }
                                ok("""[${inviteJson("iA", "PENDING", inviteeName = "A")}]""")
                            }

                            it.url.encodedPath == "/api/branches/b2/relief-invites" && it.method.value == "GET" -> {
                                ok("""[${inviteJson("iB", "PENDING", inviteeName = "B")}]""")
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadSent("b1")
            runCurrent()
            // b1's load is in flight — the branch label must NOT pre-flip (commit-stamping):
            // the screen gate would otherwise render the previous branch's keep-last.
            assertEquals(null, vm.sentBranch.value)

            // b2's panel opens while b1's load is in flight: the newer load must win.
            vm.loadSent("b2")
            runCurrent()
            assertEquals("b2", vm.sentBranch.value)
            assertEquals(
                "iB",
                vm.lastSent.value!!
                    .single()
                    .id,
            )

            // b1's late response lands — the stale stamp must NOT commit A's rows.
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(
                "iB",
                vm.lastSent.value!!
                    .single()
                    .id,
                "stale branch load must not commit",
            )
            assertEquals("b2", vm.sentBranch.value)
        }

    private fun handler(block: MockRequestHandler): MockRequestHandler = block
}
