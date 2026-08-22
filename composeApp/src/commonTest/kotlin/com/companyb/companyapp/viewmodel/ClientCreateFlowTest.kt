package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
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
import kotlinx.coroutines.delay
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
 * #348 — the client-create dialog's VM half: `createClient` Success lands the created row at
 * the head of the keep-last search cache; a backend 400 policy body is extracted into the
 * inline Error message (the create-user shape); the Loading guard collapses a double-submit
 * to one POST.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientCreateFlowTest {
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

    private fun TestScope.advanceTimeByAndRun(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun createClient_success_emits_success_and_prepends_to_search_cache() =
        runTest(testScheduler) {
            val vm = ClientViewModel(mockApiClient(createHandler()))
            // A prior search populated the cache.
            vm.onQueryChange("jo")
            advanceTimeByAndRun(300)
            val before = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c1"), actual = before.data.map { it.id })

            vm.createClient(request(id = "c9"))
            runCurrent()

            val state = assertIs<UiState.Success<ClientResponse>>(vm.createClientResult.value)
            assertEquals(expected = "c9", actual = state.data.id)
            val after = assertIs<UiState.Success<List<ClientResponse>>>(vm.searchResults.value)
            assertEquals(expected = listOf("c9", "c1"), actual = after.data.map { it.id })
        }

    @Test
    fun createClient_400_policy_body_extracts_error_message() =
        runTest(testScheduler) {
            val vm =
                ClientViewModel(
                    mockApiClient(
                        createHandler(status = HttpStatusCode.BadRequest, body = CREATE_ERROR_JSON),
                    ),
                )

            vm.createClient(request())
            runCurrent()

            val state = assertIs<UiState.Error>(vm.createClientResult.value)
            assertEquals(expected = "A client with this name already exists", actual = state.message)
        }

    @Test
    fun createClient_double_submit_while_loading_fires_one_post() =
        runTest(testScheduler) {
            var posts = 0
            val vm =
                ClientViewModel(
                    mockApiClient { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/clients" -> {
                                posts++
                                // Hold the first POST in flight across the second call.
                                delay(10_000)
                                jsonRespond(status = HttpStatusCode.OK, body = CREATED_JSON)
                            }

                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/clients" -> {
                                jsonRespond(status = HttpStatusCode.OK, body = SEARCH_JSON)
                            }

                            else -> {
                                error("unexpected request: ${request.method} ${request.url.encodedPath}")
                            }
                        }
                    },
                )

            vm.createClient(request())
            vm.createClient(request())
            advanceTimeByAndRun(20_000)

            assertEquals(expected = 1, actual = posts)
        }

    private fun request(id: String = "c9") =
        CreateClientRequest(
            id = id,
            firstName = "New",
            lastName = "Client",
            middleName = null,
            gender = Gender.M,
            age = 30,
            systolicBp = null,
            diastolicBp = null,
        )

    private fun createHandler(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = CREATED_JSON,
    ): MockRequestHandler =
        { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/clients" -> {
                    jsonRespond(status = status, body = body)
                }

                request.method == HttpMethod.Get && request.url.encodedPath == "/api/clients" -> {
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
                {"id":"c1","firstName":"John","lastName":"Doe","middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"M","age":30,"systolicBp":null,"diastolicBp":null,"medicalConditions":null}
            ]"""

        const val CREATED_JSON =
            """{"id":"c9","firstName":"New","lastName":"Client","middleName":null,"suffix":null,"phoneNumber":null,"address":null,"gender":"M","age":30,"systolicBp":null,"diastolicBp":null,"medicalConditions":null}"""

        const val CREATE_ERROR_JSON = """{"error":"A client with this name already exists"}"""
    }
}
