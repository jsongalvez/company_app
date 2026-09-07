package com.companyb.companyapp.session.rates

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.contracts.session.SetRateRequest
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * #573 — the extracted rate state owner: list loads, the single-flight save
 * (one in-flight POST so a double-tap mints a single rotation id), and
 * terminal-failure surfacing (the screen reloads pessimistically on both
 * terminal legs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRatesViewModelTest {
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
    fun loadRates_success_emits_rate_list() =
        runTest(testScheduler) {
            val harness = RatesHarness()
            val vm = SessionRatesViewModel(mockApiClient(harness.handler()))

            vm.loadRates("b1")
            advanceUntilIdle()

            val state = assertIs<UiState.Success<List<RateResponse>>>(vm.rates.value)
            assertEquals(1, state.data.size)
            assertEquals(SessionType.REGULAR, state.data.single().sessionType)
            assertEquals(1, harness.loadCount)
        }

    @Test
    fun setRate_double_tap_sends_one_request() =
        runTest(testScheduler) {
            val harness = RatesHarness()
            val vm = SessionRatesViewModel(mockApiClient(harness.handler()))
            val request = SetRateRequest("r1", SessionType.REGULAR, "2500")

            vm.setRate("b1", request)
            vm.setRate("b1", request)
            advanceUntilIdle()

            assertEquals(1, harness.saveCount)
            assertIs<UiState.Success<RateResponse>>(vm.setRateState.value)
        }

    @Test
    fun setRate_failure_surfaces_error() =
        runTest(testScheduler) {
            val harness = RatesHarness(saveStatus = HttpStatusCode.BadRequest)
            val vm = SessionRatesViewModel(mockApiClient(harness.handler()))

            vm.setRate("b1", SetRateRequest("r1", SessionType.REGULAR, "-5"))
            advanceUntilIdle()

            assertIs<UiState.Error>(vm.setRateState.value)
            assertEquals(1, harness.saveCount)
        }

    private class RatesHarness(
        var saveStatus: HttpStatusCode = HttpStatusCode.Created,
    ) {
        var loadCount = 0
        var saveCount = 0

        fun handler(): MockRequestHandler =
            { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/branches/b1/rates" -> {
                        loadCount++
                        jsonResponse(HttpStatusCode.OK, "[$RATE_JSON]")
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath == "/api/branches/b1/rates" -> {
                        saveCount++
                        jsonResponse(saveStatus, RATE_JSON)
                    }

                    else -> {
                        error("unexpected request: ${request.method} ${request.url.encodedPath}")
                    }
                }
            }
    }

    private companion object {
        const val RATE_JSON =
            """{"id":"r1","branchId":"b1","sessionType":"REGULAR","rate":"2500.00","effectiveFrom":"2026-01-01T00:00:00Z","effectiveUntil":"9999-12-31T23:59:59Z"}"""
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
