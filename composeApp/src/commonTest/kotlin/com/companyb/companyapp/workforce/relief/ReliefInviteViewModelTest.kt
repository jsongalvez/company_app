package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.workforce.ReliefCandidateResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.notification.NotificationState
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            assertEquals(1, vm.freshestReceived.value!!.size)
            assertEquals(1, receivedCalls)

            // Re-entry refires (no Loading guard once Success) but keep-last mirrors every Success.
            vm.loadReceived()
            runCurrent()
            assertEquals(2, receivedCalls)
            assertEquals(1, vm.freshestReceived.value!!.size)
        }

    @Test
    fun `accept_keeps_row_with_accepted_overlay_and_decrements_the_badge`() =
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
            // #679 — the resolved row stays in place with its outcome for the visit.
            assertEquals(1, vm.freshestReceived.value!!.size)
            val resolved = vm.resolvedThisVisit.value["i1"]
            assertNotNull(resolved)
            assertEquals(ReliefInviteStatus.ACCEPTED, resolved.outcome)
            assertEquals("i1", resolved.invite.id)
            assertEquals(0, NotificationState.inviteCount.value)
        }

    @Test
    fun `decline_keeps_row_with_declined_overlay_and_decrements_the_badge`() =
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
            // #679 — i1 stays at its position showing Declined; i2 keeps its actions.
            assertEquals(2, vm.freshestReceived.value!!.size)
            val declined = vm.resolvedThisVisit.value["i1"]
            assertNotNull(declined)
            assertEquals(ReliefInviteStatus.DECLINED, declined.outcome)
            assertNull(vm.resolvedThisVisit.value["i2"])
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
            assertEquals(1, vm.freshestReceived.value!!.size, "a failed accept keeps the row")
            assertTrue(vm.resolvedThisVisit.value.isEmpty(), "no outcome is claimed on failure")
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
            assertEquals(1, vm.sentByKey.value["b1"]!!.size)
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
            assertEquals(1, vm.sentByKey.value["b1"]!!.size)
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
            assertEquals(1, vm.freshestReceived.value!!.size)

            failNext = true
            vm.loadReceived()
            runCurrent()
            assertIs<UiState.Error>(vm.received.value)
            assertEquals(1, vm.freshestReceived.value!!.size, "keep-last survives the reload error")
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
            // b1's load is in flight — the keyed mirror has no entry yet (commit-time write, no
            // pre-set): the screen gate `sentByKey[panelBranch]` renders nothing for b1 until
            // the commit lands (the #160 commit-stamping contract, now by construction).
            assertNull(vm.sentByKey.value["b1"])

            // b2's panel opens while b1's load is in flight: the newer load must win.
            vm.loadSent("b2")
            runCurrent()
            assertEquals(
                "iB",
                vm.sentByKey.value["b2"]!!
                    .single()
                    .id,
            )

            // b1's late response lands — the loadSent stamp (newest-launch-wins) skips its
            // commit entirely: b2's entry is untouched, and the mirror never holds a
            // known-stale snapshot (the screen gate reads the current panel's key — the
            // #160 pass-1/pass-2 cross-branch bleed class, closed by construction).
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(
                "iB",
                vm.sentByKey.value["b2"]!!
                    .single()
                    .id,
                "stale branch load must not touch b2's entry",
            )
            assertNull(
                vm.sentByKey.value["b1"],
                "the stale response's commit is stamped out — a panel re-open refetches fresh",
            )
        }

    @Test
    fun `stale same-branch load never overwrites the retract-reload's fresh list`() =
        runTest(testScheduler) {
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                getCalls++
                                if (getCalls == 1) {
                                    // Panel-open load #1: snapshot taken PRE-retract, held at
                                    // +10s virtual (still in flight when retract fires).
                                    withContext(StandardTestDispatcher(testScheduler)) {
                                        kotlinx.coroutines.delay(10_000)
                                    }
                                    ok("""[${inviteJson("i1", "PENDING", inviteeName = "Alice")}]""")
                                } else {
                                    ok("""[${inviteJson("i1", "RETRACTED", inviteeName = "Alice")}]""")
                                }
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

            // Retract lands while #1 is in flight; the retract-triggered reload #2 commits the
            // fresh list (pass-1 HARD: the same-key ordering the keyed mirror cannot see — the
            // #141 resurrect class).
            vm.retractInvite("i1", "b1")
            runCurrent()
            assertEquals(
                ReliefInviteStatus.RETRACTED,
                vm.sentByKey.value["b1"]!!
                    .single()
                    .status,
            )

            // #1's pre-retract snapshot lands late — the loadSent stamp must skip its commit.
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(
                ReliefInviteStatus.RETRACTED,
                vm.sentByKey.value["b1"]!!
                    .single()
                    .status,
                "stale snapshot must not resurrect the PENDING row",
            )
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
            assertEquals(2, vm.freshestReceived.value!!.size)

            // The second load is in flight (delayed); the accept lands first.
            vm.loadReceived()
            runCurrent()
            vm.acceptInvite("i1")
            runCurrent()
            assertEquals(2, vm.freshestReceived.value!!.size, "i1 stays with its Accepted overlay")

            // The stale load lands AFTER the accept — its stamp mismatch must retain the
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
            assertEquals(1, vm.freshestReceived.value!!.size)

            vm.acceptInvite("i1")
            runCurrent()
            // 409 → handled (no Error) → reload → the stale row leaves.
            assertIs<UiState.Idle>(vm.acceptResult.value)
            assertEquals(2, reloadCalls)
            assertEquals(0, vm.freshestReceived.value!!.size)
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
            assertEquals(1, vm.freshestReceived.value!!.size)

            // The second load is in flight when the accept 409s. The 409 is authoritative
            // server confirmation — the row leaves locally (removal) and the stamp bump
            // protects the in-flight snapshot from committing (the #141 resurrect class).
            vm.loadReceived()
            runCurrent()
            vm.acceptInvite("i1")
            runCurrent()
            assertIs<UiState.Idle>(vm.acceptResult.value)
            assertEquals(0, vm.freshestReceived.value!!.size, "the 409 removes the row locally")

            // The pre-conflict load lands with a stale stamp: substitution converges to the
            // post-409 list (already empty) instead of resurrecting i1.
            advanceTimeBy(20_000)
            runCurrent()
            val state = vm.received.value
            assertIs<UiState.Success<List<ReliefInviteResponse>>>(state)
            assertEquals(0, state.data.size, "the resolved row must not resurrect")
            assertEquals(0, vm.freshestReceived.value!!.size)
        }

    @Test
    fun `loadAccepted_commits_the_branch_keyed_mirror`() =
        runTest(testScheduler) {
            var acceptedCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites/accepted" -> {
                                acceptedCalls++
                                ok(
                                    """[${inviteJson(
                                        "i1",
                                        "ACCEPTED",
                                        date = "2026-08-20",
                                        inviteeName = "Colleague",
                                    )}]""",
                                )
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadAccepted("b1")
            runCurrent()
            assertEquals(1, acceptedCalls)
            assertEquals(
                "i1",
                vm.acceptedByKey.value["b1"]!!
                    .single()
                    .id,
            )
        }

    @Test
    fun `revoke_success_reloads_both_revocation_surfaces`() =
        runTest(testScheduler) {
            var acceptedCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites/accepted" -> {
                                acceptedCalls++
                                // Call #1 serves the pre-revoke row; later calls are the
                                // post-revoke reloads — the row left the actionable list.
                                if (acceptedCalls == 1) {
                                    ok("""[${inviteJson("i1", "ACCEPTED", inviteeName = "Alice")}]""")
                                } else {
                                    ok("[]")
                                }
                            }

                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                ok("""[${inviteJson("i1", "REVOKED", inviteeName = "Alice")}]""")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/revoke" -> {
                                ok(inviteJson("i1", "REVOKED", inviteeName = "Alice"))
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadAccepted("b1")
            runCurrent()
            assertEquals(1, vm.acceptedByKey.value["b1"]!!.size)

            vm.revokeInvite("i1", "b1")
            runCurrent()
            assertIs<UiState.Success<Unit>>(vm.revokeResult.value)
            assertEquals(2, acceptedCalls, "the accepted list reloads after the revoke")
            assertEquals(0, vm.acceptedByKey.value["b1"]!!.size)
            assertEquals(
                ReliefInviteStatus.REVOKED,
                vm.sentByKey.value["b1"]!!
                    .single()
                    .status,
                "the sent list re-renders the row as REVOKED status text",
            )
        }

    @Test
    fun `revoke_400_surfaces_the_server_error_message`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites/i1/revoke" -> {
                                respond(
                                    content = ByteReadChannel("""{"error":"This relief duty has already started"}"""),
                                    status = HttpStatusCode.BadRequest,
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

            vm.revokeInvite("i1", "b1")
            runCurrent()
            val state = vm.revokeResult.value
            assertIs<UiState.Error>(state)
            assertEquals("This relief duty has already started", state.message)
        }

    @Test
    fun `revoke_conflict_is_authoritative_and_reloads_without_an_error`() =
        runTest(testScheduler) {
            var acceptedCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites/accepted" -> {
                                acceptedCalls++
                                ok("[]")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/revoke" -> {
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

            vm.revokeInvite("i1", "b1")
            runCurrent()
            // Another member revoked first — authoritative, not an error: Idle + reload.
            assertIs<UiState.Idle>(vm.revokeResult.value)
            assertEquals(1, acceptedCalls, "the conflict reload converges server truth")
        }

    @Test
    fun `accept_twice_decrements_the_badge_once`() =
        runTest(testScheduler) {
            NotificationState.setInviteCount(2)
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

            // Double-tap fires two POSTs; the overlay dedupes the second resolution.
            vm.acceptInvite("i1")
            vm.acceptInvite("i1")
            runCurrent()
            assertEquals(1, vm.resolvedThisVisit.value.size)
            val accepted = vm.resolvedThisVisit.value["i1"]
            assertNotNull(accepted)
            assertEquals(ReliefInviteStatus.ACCEPTED, accepted.outcome)
            assertEquals(1, NotificationState.inviteCount.value)
        }

    @Test
    fun `duplicate_accept_after_success_keeps_overlay_and_single_decrement`() =
        runTest(testScheduler) {
            NotificationState.setInviteCount(2)
            var acceptCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-invites" -> {
                                ok("[${inviteJson("i1", "PENDING")}]")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/accept" -> {
                                acceptCalls++
                                if (acceptCalls == 1) {
                                    ok(inviteJson("i1", "ACCEPTED"))
                                } else {
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

            vm.acceptInvite("i1")
            runCurrent()
            val accepted = vm.resolvedThisVisit.value["i1"]
            assertNotNull(accepted)
            assertEquals(ReliefInviteStatus.ACCEPTED, accepted.outcome)

            // The duplicate POST 409s: our confirmed resolution stands — the overlay and the
            // single badge decrement survive, with no reload wiping the in-place outcome.
            vm.acceptInvite("i1")
            runCurrent()
            assertIs<UiState.Idle>(vm.acceptResult.value)
            val retained = vm.resolvedThisVisit.value["i1"]
            assertNotNull(retained)
            assertEquals(ReliefInviteStatus.ACCEPTED, retained.outcome)
            assertEquals(1, vm.freshestReceived.value!!.size)
            assertEquals(1, NotificationState.inviteCount.value)
        }

    @Test
    fun `mergeReceivedRows_keeps_live_positions_and_appends_retained_snapshots`() {
        val pending =
            ReliefInviteResponse(
                id = "i1",
                branchId = "b1",
                branchName = "Branch A",
                branchDayId = "bd1",
                date = "2026-08-16",
                invitedBy = "inviter",
                inviterName = "Inviter",
                invitee = "invitee",
                inviteeName = "Invitee",
                status = ReliefInviteStatus.PENDING,
                createdAt = "2026-08-01T00:00:00Z",
            )
        val retained =
            ReliefInviteResponse(
                id = "i0",
                branchId = "b1",
                branchName = "Branch A",
                branchDayId = "bd0",
                date = "2026-08-15",
                invitedBy = "inviter",
                inviterName = "Inviter",
                invitee = "invitee",
                inviteeName = "Invitee",
                status = ReliefInviteStatus.PENDING,
                createdAt = "2026-08-01T00:00:00Z",
            )
        val resolved =
            mapOf(
                "i1" to ResolvedInvite(pending, ReliefInviteStatus.ACCEPTED),
                "i0" to ResolvedInvite(retained, ReliefInviteStatus.DECLINED),
            )

        // The live row keeps its position with the outcome applied; the reloaded-away row
        // renders from its retained snapshot after the live rows.
        val merged = mergeReceivedRows(listOf(pending), resolved)
        assertEquals(listOf("i1", "i0"), merged.map { it.invite.id })
        assertEquals(
            listOf(ReliefInviteStatus.ACCEPTED, ReliefInviteStatus.DECLINED),
            merged.map { it.outcome },
        )
    }

    @Test
    fun `sent load failure surfaces retryable error and retains the list`() =
        runTest(testScheduler) {
            var failNext = false
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                if (failNext) {
                                    respond(
                                        content = ByteReadChannel("""{"error":"boom"}"""),
                                        status = HttpStatusCode.BadRequest,
                                        headers =
                                            headersOf(
                                                HttpHeaders.ContentType,
                                                ContentType.Application.Json.toString(),
                                            ),
                                    )
                                } else {
                                    ok("""[${inviteJson("i1", "PENDING", inviteeName = "Alice")}]""")
                                }
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
            assertEquals(1, vm.sentByKey.value["b1"]!!.size)
            assertNull(vm.sentLoadError.value)

            failNext = true
            vm.loadSent("b1")
            runCurrent()
            assertEquals("Couldn't load sent invites", vm.sentLoadError.value)
            assertEquals(
                1,
                vm.sentByKey.value["b1"]!!.size,
                "the mirror retains the list across the refresh failure",
            )

            failNext = false
            vm.loadSent("b1")
            runCurrent()
            assertNull(vm.sentLoadError.value, "a successful retry clears the error")
        }

    @Test
    fun `accepted load failure surfaces retryable error and retains the list`() =
        runTest(testScheduler) {
            var failNext = false
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites/accepted" -> {
                                if (failNext) {
                                    respond(
                                        content = ByteReadChannel("""{"error":"boom"}"""),
                                        status = HttpStatusCode.BadRequest,
                                        headers =
                                            headersOf(
                                                HttpHeaders.ContentType,
                                                ContentType.Application.Json.toString(),
                                            ),
                                    )
                                } else {
                                    ok("""[${inviteJson("i1", "ACCEPTED", inviteeName = "Alice")}]""")
                                }
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.loadAccepted("b1")
            runCurrent()
            assertEquals(1, vm.acceptedByKey.value["b1"]!!.size)

            failNext = true
            vm.loadAccepted("b1")
            runCurrent()
            assertEquals("Couldn't load accepted duties", vm.acceptedLoadError.value)
            assertEquals(
                1,
                vm.acceptedByKey.value["b1"]!!.size,
                "the mirror retains the list across the refresh failure",
            )
        }

    @Test
    fun `rapid reissue resolves to the newer query`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-candidates" -> {
                                val query = it.url.parameters["q"].orEmpty()
                                if (query == "al") {
                                    withContext(StandardTestDispatcher(testScheduler)) {
                                        kotlinx.coroutines.delay(10_000)
                                    }
                                    ok("""[{"id":"u1","username":"alice","displayName":"Alice"}]""")
                                } else {
                                    ok("""[{"id":"u2","username":"bob","displayName":"Bob"}]""")
                                }
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            // Back-to-back reissue (date-switch shape): the newer query wins and a late
            // superseded landing — via cancellation or the generation guard — never overwrites it.
            vm.searchCandidates("b1", "al", "2026-08-16")
            vm.searchCandidates("b1", "bo", "2026-08-17")
            runCurrent()
            val second = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(second)
            assertEquals("u2", second.data.single().id)

            advanceTimeBy(20_000)
            runCurrent()
            val after = vm.candidates.value
            assertIs<UiState.Success<List<ReliefCandidateResponse>>>(after)
            assertEquals(
                "u2",
                after.data.single().id,
                "the superseded landing must not overwrite the newer results",
            )
        }

    @Test
    fun `rapid double send issues a single post`() =
        runTest(testScheduler) {
            var postCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "POST" -> {
                                postCalls++
                                withContext(StandardTestDispatcher(testScheduler)) {
                                    kotlinx.coroutines.delay(5_000)
                                }
                                ok(inviteJson("i1", "PENDING", inviteeName = "Alice"))
                            }

                            else -> {
                                ok("[]")
                            }
                        }
                    },
                )
            val vm = ReliefInviteViewModel(apiClient)
            vm.sendInvite("b1", "u1", "2026-08-16")
            vm.sendInvite("b1", "u1", "2026-08-16")
            runCurrent()
            assertEquals(1, postCalls, "the second tap while pending must not re-post")

            advanceTimeBy(10_000)
            runCurrent()
            assertIs<UiState.Success<Unit>>(vm.createResult.value)

            // A send after the first lands is a genuine consecutive invitation, not a duplicate.
            vm.sendInvite("b1", "u2", "2026-08-16")
            runCurrent()
            assertEquals(2, postCalls)
        }

    @Test
    fun `retract conflict converges without an error`() =
        runTest(testScheduler) {
            var sentCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/branches/b1/relief-invites" && it.method.value == "GET" -> {
                                sentCalls++
                                ok("[]")
                            }

                            it.url.encodedPath == "/api/relief-invites/i1/retract" -> {
                                respond(
                                    content = ByteReadChannel("""{"error":"already resolved"}"""),
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
            vm.retractInvite("i1", "b1")
            runCurrent()
            assertIs<UiState.Idle>(vm.retractResult.value)
            assertEquals(1, sentCalls, "the conflict reload converges server truth")
        }

    private fun handler(block: MockRequestHandler): MockRequestHandler = block
}
