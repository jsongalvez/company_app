package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.network.mockApiClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthViewModelTest {
    @Test
    fun loginSuccessTransitionsToSuccess() =
        runBlocking {
            val apiClient =
                mockApiClient(
                    status = HttpStatusCode.OK,
                    body = """{"token": "fake-jwt"}""",
                )
            val viewModel = AuthViewModel(apiClient, scope = this)

            assertEquals(UiState.Idle, viewModel.loginState.value)

            viewModel.login("test", "pass").join()

            val state = viewModel.loginState.value
            val success = assertIs<UiState.Success<LoginResponse>>(state)
            assertEquals("fake-jwt", success.data.token)
        }

    @Test
    fun loginFailureTransitionsToError() =
        runBlocking {
            val apiClient =
                mockApiClient(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"error": "invalid"}""",
                )
            val viewModel = AuthViewModel(apiClient, scope = this)

            assertEquals(UiState.Idle, viewModel.loginState.value)

            viewModel.login("test", "wrong").join()

            val state = viewModel.loginState.value
            val error = assertIs<UiState.Error>(state)
            assertEquals("Login failed: 401", error.message)
        }
}
