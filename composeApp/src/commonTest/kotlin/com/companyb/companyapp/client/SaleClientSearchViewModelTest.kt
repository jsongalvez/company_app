package com.companyb.companyapp.client

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * #610 — the sale buyer-search owner behind [ClientSearchApi]: debounced search, retry,
 * and mutation reconciliation through the shared [ClientSearcher]. No edit/anonymize surface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaleClientSearchViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ClientState.clear()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        ClientState.clear()
    }

    @Test
    fun onQueryChange_fires_search_after_300ms_debounce() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = SaleClientSearchViewModel(mockApiClient(searchHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(299)
            assertEquals(expected = emptyList<String>(), actual = recorded)
            advanceTimeByAndRun(1)

            assertEquals(expected = listOf("jo"), actual = recorded)
            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
        }

    @Test
    fun onQueryChange_below_two_chars_stays_idle_and_fires_no_request() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = SaleClientSearchViewModel(mockApiClient(searchHandler(searchQueries = recorded)))

            vm.onQueryChange("j")
            advanceTimeByAndRun(100)
            advanceTimeByAndRun(300)

            assertIs<UiState.Idle>(vm.searchResults.value)
            assertEquals(expected = emptyList<String>(), actual = recorded)
        }

    @Test
    fun retrySearch_refires_latest_query() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = SaleClientSearchViewModel(mockApiClient(searchHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.retrySearch()
            runCurrent()

            assertEquals(expected = listOf("jo", "jo"), actual = recorded)
        }

    @Test
    fun applyClientMutation_replace_updates_loaded_row() =
        runTest(testScheduler) {
            var calls = 0
            val vm =
                SaleClientSearchViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                calls++
                                // The mutation refresh re-search lands the current server state.
                                val body = if (calls == 1) SEARCH_JSON else UPDATED_JSON
                                jsonRespond(status = HttpStatusCode.OK, body = body)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)

            vm.applyClientMutation(ClientMutation("c1", fixtureClient("c1").copy(firstName = "Jane")))
            runCurrent()

            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = "Jane", actual = state.data.first { it.id == "c1" }.firstName)
        }

    @Test
    fun applyClientMutation_remove_drops_anonymized_row() =
        runTest(testScheduler) {
            var calls = 0
            val vm =
                SaleClientSearchViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/clients" -> {
                                calls++
                                // Post-anonymize refresh: the server no longer returns the row.
                                val body = if (calls == 1) SEARCH_JSON else "[]"
                                jsonRespond(status = HttpStatusCode.OK, body = body)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)

            vm.applyClientMutation(ClientMutation("c1", null))
            runCurrent()

            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = emptyList(), actual = state.data)
        }

    @Test
    fun applyClientMutation_snapshot_without_refresh_leaves_search_untouched() =
        runTest(testScheduler) {
            val recorded = mutableListOf<String>()
            val vm = SaleClientSearchViewModel(mockApiClient(searchHandler(searchQueries = recorded)))

            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            vm.applyClientMutation(ClientMutation("c1", fixtureClient("c1"), refreshSearch = false))
            runCurrent()

            assertEquals(expected = listOf("jo"), actual = recorded)
            val state = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = state.data.map { it.id })
        }

    private fun TestScope.advanceTimeByAndRun(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private fun fixtureClient(id: String): ClientResponse =
        ClientResponse(
            id = id,
            firstName = "John",
            lastName = "Doe",
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = Gender.M,
            age = 30,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
            sessionCount = 0,
        )

    private fun searchHandler(searchQueries: MutableList<String>? = null): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/clients" -> {
                    searchQueries?.add(request.url.parameters["q"].orEmpty())
                    jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
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
        const val SEARCH_JSON =
            """[
                {"id":"c1","firstName":"John","lastName":"Doe","middleName":null,"suffix":null,"phoneNumber":"09171234567","address":null,"gender":"M","age":30,"systolicBp":120,"diastolicBp":80,"medicalConditions":null,"sessionCount":0}
            ]"""
        const val UPDATED_JSON =
            """[
                {"id":"c1","firstName":"Jane","lastName":"Doe","middleName":null,"suffix":null,"phoneNumber":"09171234567","address":null,"gender":"M","age":30,"systolicBp":120,"diastolicBp":80,"medicalConditions":null,"sessionCount":0}
            ]"""
    }
}
