package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
 * #351 — ReliefAccessViewModel flows: caller-relative discovery load (keep-last), Grant/Deny/
 * request actions (stamp bump + reload), candidates fetch, and the #141/#165 stamp guard — a
 * pre-action load landing late must not resurrect the pre-action snapshot. Entry-scoped VM —
 * no poll loop, so plain runTest drains are safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReliefAccessViewModelTest {
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

    private fun MockRequestHandleScope.ok(body: String) =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun handler(block: MockRequestHandler): MockRequestHandler = block

    private fun requestJson(
        id: String,
        status: String,
        targetUser: String = "target-1",
        requestedBy: String = "requester-1",
    ): String =
        """{"id":"$id","branchDayId":"day-1","requestedBy":"$requestedBy",""" +
            """"requestStatus":"$status","targetUser":"$targetUser"}"""

    @Test
    fun `loadRequests commits success and mirrors keep last`() =
        runTest(testScheduler) {
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access" &&
                                it.method == HttpMethod.Get &&
                                it.url.parameters["branchDayId"] == "day-1" -> {
                                getCalls++
                                ok("[${requestJson("r1", "PENDING")}]")
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.loadRequests("day-1")
            runCurrent()
            val state = vm.requests.value
            assertIs<UiState.Success<List<ReliefAccessResponse>>>(state)
            assertEquals(1, state.data.size)
            assertEquals(1, vm.freshestRequests.value!!.size)
            assertEquals(1, getCalls)
        }

    @Test
    fun `grant patches and reloads the list`() =
        runTest(testScheduler) {
            var grantCalls = 0
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                getCalls++
                                // First load: pending; the post-grant reload: granted.
                                ok("[${requestJson("r1", if (getCalls == 1) "PENDING" else "GRANTED")}]")
                            }

                            it.url.encodedPath == "/api/relief-access/r1/grant" &&
                                it.method == HttpMethod.Patch -> {
                                grantCalls++
                                ok(requestJson("r1", "GRANTED"))
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.loadRequests("day-1")
            runCurrent()
            vm.grantAccess("r1", "day-1")
            runCurrent()
            assertEquals(1, grantCalls)
            assertEquals(2, getCalls, "grant must trigger a reload")
            val state = vm.requests.value
            assertIs<UiState.Success<List<ReliefAccessResponse>>>(state)
            assertEquals(
                "GRANTED",
                state.data
                    .single()
                    .requestStatus.name,
            )
        }

    @Test
    fun `deny patches and reloads the list`() =
        runTest(testScheduler) {
            var denyCalls = 0
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                getCalls++
                                ok("[${requestJson("r1", if (getCalls == 1) "PENDING" else "DENIED")}]")
                            }

                            it.url.encodedPath == "/api/relief-access/r1/deny" &&
                                it.method == HttpMethod.Patch -> {
                                denyCalls++
                                ok(requestJson("r1", "DENIED"))
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.loadRequests("day-1")
            runCurrent()
            vm.denyAccess("r1", "day-1")
            runCurrent()
            assertEquals(1, denyCalls)
            assertEquals(2, getCalls)
            val state = vm.requests.value
            assertIs<UiState.Success<List<ReliefAccessResponse>>>(state)
            assertEquals(
                "DENIED",
                state.data
                    .single()
                    .requestStatus.name,
            )
        }

    @Test
    fun `requestAccess posts to the request endpoint and reloads`() =
        runTest(testScheduler) {
            var postCalls = 0
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                getCalls++
                                ok("[${requestJson("r9", "PENDING")}]")
                            }

                            it.url.encodedPath == "/api/relief-access/request" &&
                                it.method == HttpMethod.Post -> {
                                postCalls++
                                ok(requestJson("r9", "PENDING"))
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.requestAccess(
                ReliefAccessRequest(requestId = "r9", branchDayId = "day-1", targetUserId = "target-1"),
                "day-1",
            )
            runCurrent()
            assertEquals(1, postCalls)
            assertEquals(1, getCalls, "a created request must trigger a reload")
            assertIs<UiState<Unit>>(vm.requestResult.value).let { state ->
                assertTrue(state is UiState.Success)
            }
        }

    @Test
    fun `loadCandidates fetches checked-in users for the branch day`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access/candidates" &&
                                it.method == HttpMethod.Get &&
                                it.url.parameters["branchDayId"] == "day-1" -> {
                                ok("""[{"userId":"u1","displayName":"Ana"}]""")
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.loadCandidates("day-1")
            runCurrent()
            val state = vm.candidates.value
            assertIs<UiState.Success<List<*>>>(state)
            assertEquals(1, state.data.size)
        }

    @Test
    fun `stale load after grant does not resurrect the pre action snapshot`() =
        runTest(testScheduler) {
            var getCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                getCalls++
                                if (getCalls == 2) {
                                    // The stale load: started before the grant, lands after it —
                                    // serving the PRE-grant snapshot.
                                    withContext(StandardTestDispatcher(testScheduler)) {
                                        kotlinx.coroutines.delay(10_000)
                                    }
                                    ok("[${requestJson("r1", "PENDING")}, ${requestJson("r2", "PENDING")}]")
                                } else if (getCalls >= 3) {
                                    // Re-issued loads serve post-grant server truth AND a row the
                                    // local substitution could never know (pins the re-issue).
                                    ok("[${requestJson("r2", "GRANTED")}, ${requestJson("r3", "PENDING")}]")
                                } else {
                                    ok("[${requestJson("r1", "PENDING")}, ${requestJson("r2", "PENDING")}]")
                                }
                            }

                            it.url.encodedPath == "/api/relief-access/r1/grant" -> {
                                ok(requestJson("r1", "GRANTED"))
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefAccessViewModel(apiClient)
            vm.loadRequests("day-1")
            runCurrent()
            assertEquals(2, vm.freshestRequests.value!!.size)

            // The second load is in flight (delayed); the grant lands first.
            vm.loadRequests("day-1")
            runCurrent()
            vm.grantAccess("r1", "day-1")
            runCurrent()

            // The stale load lands AFTER the grant — its stamp mismatch must substitute the
            // post-action mirror, not resurrect r1 as PENDING; the re-issue converges truth (r3).
            advanceTimeBy(20_000)
            runCurrent()
            val state = vm.requests.value
            assertIs<UiState.Success<List<ReliefAccessResponse>>>(state)
            val ids = state.data.map { it.id }
            assertFalse(ids.contains("r1"), "the granted row's PENDING form must not resurrect: $ids")
            assertTrue(ids.contains("r3"), "the re-issued load must converge server truth: $ids")
        }
}
