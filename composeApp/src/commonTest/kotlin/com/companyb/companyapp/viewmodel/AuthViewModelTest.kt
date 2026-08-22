package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginSuccessTransitionsToSuccess() =
        runTest {
            val apiClient =
                mockApiClient(
                    status = HttpStatusCode.OK,
                    body = """{"token": "fake-jwt"}""",
                )
            val viewModel = AuthViewModel(apiClient)

            assertEquals(UiState.Idle, viewModel.loginState.value)

            viewModel.login("test", "pass").join()

            val state = viewModel.loginState.value
            val success = assertIs<UiState.Success<LoginResponse>>(state)
            assertEquals("fake-jwt", success.data.token)
        }

    @Test
    fun loginFailureTransitionsToError() =
        runTest {
            val apiClient =
                mockApiClient(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"error": "invalid"}""",
                )
            val viewModel = AuthViewModel(apiClient)

            assertEquals(UiState.Idle, viewModel.loginState.value)

            viewModel.login("test", "wrong").join()

            val state = viewModel.loginState.value
            val error = assertIs<UiState.Error>(state)
            assertEquals("login failed: 401", error.message)
        }

    // #350 — public accept-invite: Success on the body-less 204; the backend's 400
    // `{"error": ...}` body (invalid / used / expired / weak password) surfaces verbatim.
    @Test
    fun acceptInviteSuccessTransitionsToSuccess() =
        runTest {
            val apiClient =
                mockApiClient { request ->
                    respond(
                        content = ByteReadChannel(""),
                        status =
                            if (request.url.encodedPath == ApiRoutes.AUTH_ACCEPT_INVITE) {
                                HttpStatusCode.NoContent
                            } else {
                                HttpStatusCode.NotFound
                            },
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val viewModel = AuthViewModel(apiClient)

            viewModel.acceptInvite("single-use-code", "valid-password").join()

            assertIs<UiState.Success<Unit>>(viewModel.acceptInviteState.value)
        }

    @Test
    fun acceptInviteErrorSurfacesBackendMessage() =
        runTest {
            val apiClient =
                mockApiClient(
                    status = HttpStatusCode.BadRequest,
                    body = """{"error": "This invite code has already been used"}""",
                )
            val viewModel = AuthViewModel(apiClient)

            viewModel.acceptInvite("spent-code", "valid-password").join()

            val state = viewModel.acceptInviteState.value
            val error = assertIs<UiState.Error>(state)
            assertEquals("This invite code has already been used", error.message)
        }

    @Test
    fun authRequestsUseSharedRouteConstants() =
        runTest {
            val paths = mutableListOf<String>()
            val apiClient =
                mockApiClient { request ->
                    paths += request.url.encodedPath
                    respond(
                        content =
                            ByteReadChannel(
                                if (request.url.encodedPath == ApiRoutes.AUTH_LOGIN) {
                                    "{\"token\": \"fake-jwt\"}"
                                } else {
                                    ""
                                },
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val viewModel = AuthViewModel(apiClient)

            viewModel.login("test", "pass").join()

            assertEquals(listOf(ApiRoutes.AUTH_LOGIN), paths)
        }
}
