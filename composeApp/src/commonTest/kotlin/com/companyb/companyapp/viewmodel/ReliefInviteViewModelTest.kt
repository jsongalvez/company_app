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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
                                // 400 — a server-side refusal, NOT a 409 (which the VM treats as
                                // "already resolved" and reloads instead of surfacing).
                                respond(
                                    content = ByteReadChannel("""{"error":"bad request"}"""),
                                    status = HttpStatusCode.BadRequest,
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
            var alCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-candidates" -> {
                                val query = it.url.parameters["q"].orEmpty()
                                if (query == "al") {
                                    alCalls++
                                    // The first search stays in flight until the newer query
                                    // cancels it — without the delay, cancel() would hit a dead
                                    // job and the test would pass vacuously (P5 finding 3).
                                    withContext(StandardTestDispatcher(testScheduler)) {
                                        kotlinx.coroutines.delay(10_000)
                                    }
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
            assertIs<UiState.Loading>(vm.candidates.value)
            assertEquals(1, alCalls)

            vm.searchCandidates("b1", "bo", "2026-08-16")
            runCurrent()
            val second = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(second)
            assertEquals(0, second.data.size, "the newer query wins")

            // The cancelled search's response must never commit — even after its delay elapses.
            advanceTimeBy(20_000)
            runCurrent()
            val after = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(after)
            assertEquals(0, after.data.size, "the cancelled in-flight response must not commit")
            assertEquals(1, alCalls)
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

    @Test
    fun `stale received load after accept does not resurrect the resolved row`() =
        runTest(testScheduler) {
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" && it.method.value == "GET" -> {
                                getCalls++
                                if (getCalls == 2) {
                                    // The stale load: started before the accept, lands after it —
                                    // serving the PRE-accept snapshot.
                                    withContext(
                                        StandardTestDispatcher(testScheduler),
                                    ) { kotlinx.coroutines.delay(10_000) }
                                    ok("[${inviteJson("i1", "PENDING")}, ${inviteJson("i2", "PENDING")}]")
                                } else if (getCalls >= 3) {
                                    // Re-issued loads run after the accept: the server serves the
                                    // post-accept state — AND a new invite (i3) the local
                                    // substitution could never know (pins the re-issue's distinct
                                    // job — server-truth convergence, P5 finding 1).
                                    ok("[${inviteJson("i2", "PENDING")}, ${inviteJson("i3", "PENDING")}]")
                                } else {
                                    ok("[${inviteJson("i1", "PENDING")}, ${inviteJson("i2", "PENDING")}]")
                                }
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/accept" -> {
                                ok(inviteJson("i1", "ACCEPTED"))
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
            assertEquals(2, vm.lastReceived.value!!.size)

            // The second load is in flight (delayed); the accept lands first.
            vm.loadReceived()
            runCurrent()
            vm.acceptInvite("i1")
            runCurrent()
            assertEquals(1, vm.lastReceived.value!!.size, "i1 left in-session")

            // The stale load lands AFTER the accept — its stamp mismatch must substitute the
            // post-action list, not resurrect i1; the re-issued load then converges server
            // truth (i3 lands, i1 stays gone).
            advanceTimeBy(20_000)
            runCurrent()
            val state = vm.received.value
            assertIs<UiState.Success<List<ReliefInviteResponse>>>(state)
            val ids = state.data.map { it.id }
            assertFalse(ids.contains("i1"), "the accepted row must not resurrect: $ids")
            assertTrue(ids.contains("i3"), "the re-issued load must converge server truth: $ids")
        }

    @Test
    fun `accept conflict reloads and drops the stale row`() =
        runTest(testScheduler) {
            var reloadCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" && it.method.value == "GET" -> {
                                reloadCalls++
                                if (reloadCalls == 1) {
                                    ok("[${inviteJson("i1", "PENDING")}]")
                                } else {
                                    // The server already resolved i1 elsewhere — the reload serves it gone.
                                    ok("[]")
                                }
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/accept" -> {
                                respond(
                                    content = ByteReadChannel("""{"error":"already responded"}"""),
                                    status = HttpStatusCode.Conflict,
                                    headers =
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        ),
                                )
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

            vm.acceptInvite("i1")
            runCurrent()
            // 409 → handled (no Error) → reload → the stale row leaves.
            assertIs<UiState.Idle>(vm.acceptResult.value)
            assertEquals(2, reloadCalls)
            assertEquals(0, vm.lastReceived.value!!.size)
        }

    @Test
    fun `pre-conflict in-flight load cannot resurrect the resolved row`() =
        runTest(testScheduler) {
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" && it.method.value == "GET" -> {
                                getCalls++
                                if (getCalls == 1) {
                                    ok("[${inviteJson("i1", "PENDING")}]")
                                } else if (getCalls == 2) {
                                    // The pre-conflict load: launched before the 409, lands after —
                                    // serving the PRE-resolution snapshot (i1 still PENDING).
                                    withContext(StandardTestDispatcher(testScheduler)) {
                                        kotlinx.coroutines.delay(10_000)
                                    }
                                    ok("[${inviteJson("i1", "PENDING")}]")
                                } else {
                                    // Any load after the 409 (the conflict reload, the
                                    // substitution's re-issue) serves post-resolution truth.
                                    ok("[]")
                                }
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/accept" -> {
                                respond(
                                    content = ByteReadChannel("""{"error":"already responded"}"""),
                                    status = HttpStatusCode.Conflict,
                                    headers =
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        ),
                                )
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

            // The second load is in flight when the accept 409s. The 409 is authoritative
            // server confirmation — the row leaves locally (removal) and the stamp bump
            // protects the in-flight snapshot from committing (the #141 resurrect class).
            vm.loadReceived()
            runCurrent()
            vm.acceptInvite("i1")
            runCurrent()
            assertIs<UiState.Idle>(vm.acceptResult.value)
            assertEquals(0, vm.lastReceived.value!!.size, "the 409 removes the row locally")

            // The pre-conflict load lands with a stale stamp: substitution converges to the
            // post-409 list (already empty) instead of resurrecting i1.
            advanceTimeBy(20_000)
            runCurrent()
            val state = vm.received.value
            assertIs<UiState.Success<List<ReliefInviteResponse>>>(state)
            assertEquals(0, state.data.size, "the resolved row must not resurrect")
            assertEquals(0, vm.lastReceived.value!!.size)
        }

    private fun handler(block: MockRequestHandler): MockRequestHandler = block
}
