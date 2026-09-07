package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.async.UiState
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #388 — ReliefDayViewModel: the deep-link destination resolves the tapped branch's display
 * name from the existing branch-options read (null fallback on miss/failure), and Retry
 * re-issues BOTH legs — a failed day load and a failed name resolution are each recovered
 * by the next load() call. Entry-scoped VM — plain runTest drains are safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReliefDayViewModelTest {
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

    private fun handler(block: MockRequestHandler): MockRequestHandler = block

    private fun MockRequestHandleScope.ok(body: String) =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.serverError() =
        respond(
            content = ByteReadChannel("""{"message":"boom"}"""),
            status = HttpStatusCode.InternalServerError,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    @Test
    fun `load resolves the tapped branch name alongside the day rows`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access/branch-options" &&
                                it.method == HttpMethod.Get -> {
                                ok(
                                    """[{"branchId":"b-0","branchName":"Other","branchType":"CLINIC"},""" +
                                        """{"branchId":"b-1","branchName":"North Branch","branchType":"CLINIC"}]""",
                                )
                            }

                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                ok(
                                    """[{"id":"r1","branchDayId":"bd-1","requestedBy":"requester-1","requestStatus":"PENDING"}]""",
                                )
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefDayViewModel(apiClient, branchId = "b-1", date = "2026-08-23")
            vm.load()
            runCurrent()
            assertEquals("North Branch", vm.branchName.value)
            assertRequestsSuccess(vm)
        }

    @Test
    fun `unknown branch id degrades to null name without touching the request list`() =
        runTest(testScheduler) {
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access/branch-options" &&
                                it.method == HttpMethod.Get -> {
                                ok("""[{"branchId":"b-0","branchName":"Other","branchType":"CLINIC"}]""")
                            }

                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                ok("""[]""")
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefDayViewModel(apiClient, branchId = "b-gone", date = "2026-08-23")
            vm.load()
            runCurrent()
            assertNull(vm.branchName.value)
            assertRequestsSuccess(vm)
        }

    @Test
    fun `retry re-issues a failed day load and the pending name resolution`() =
        runTest(testScheduler) {
            var optionCalls = 0
            var dayCalls = 0
            val apiClient =
                mockApiClient(
                    handler {
                        when {
                            it.url.encodedPath == "/api/relief-access/branch-options" &&
                                it.method == HttpMethod.Get -> {
                                optionCalls++
                                if (optionCalls == 1) {
                                    serverError()
                                } else {
                                    ok(
                                        """[{"branchId":"b-1","branchName":"North Branch","branchType":"CLINIC"}]""",
                                    )
                                }
                            }

                            it.url.encodedPath == "/api/relief-access" && it.method == HttpMethod.Get -> {
                                dayCalls++
                                if (dayCalls == 1) {
                                    serverError()
                                } else {
                                    ok(
                                        """[{"id":"r1","branchDayId":"bd-1","requestedBy":"requester-1","requestStatus":"PENDING"}]""",
                                    )
                                }
                            }

                            else -> {
                                error("unexpected ${it.method.value} ${it.url}")
                            }
                        }
                    },
                )
            val vm = ReliefDayViewModel(apiClient, branchId = "b-1", date = "2026-08-23")
            vm.load()
            runCurrent()
            assertEquals(1, dayCalls)
            assertEquals(1, optionCalls)
            assertNull(vm.branchName.value)

            vm.load()
            runCurrent()
            assertEquals(2, dayCalls, "Retry must re-issue the day load")
            assertEquals(2, optionCalls, "Retry must re-attempt an unresolved branch name")
            assertEquals("North Branch", vm.branchName.value)
            assertRequestsSuccess(vm)
        }

    private fun assertRequestsSuccess(vm: ReliefDayViewModel) {
        kotlin.test.assertIs<UiState.Success<List<*>>>(vm.requests.value)
    }
}
