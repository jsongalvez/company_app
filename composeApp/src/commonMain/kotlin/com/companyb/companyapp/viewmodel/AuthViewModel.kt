package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.RegisterRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AuthVM")

    private val _loginState = MutableStateFlow<UiState<LoginResponse>>(UiState.Idle)
    val loginState: StateFlow<UiState<LoginResponse>> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val registerState: StateFlow<UiState<Unit>> = _registerState.asStateFlow()

    private val _logoutState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val logoutState: StateFlow<UiState<Unit>> = _logoutState.asStateFlow()

    fun login(
        username: String,
        password: String,
    ): Job =
        handler.launch(
            state = _loginState,
            operation = "login",
            endpoint = "POST /auth/login",
            entryMessage = "login attempt for username=$username",
            block = {
                apiClient.httpClient.post("/auth/login") {
                    setBody(LoginRequest(username, password))
                }
            },
            transform = { it.body() },
        )

    fun register(
        username: String,
        password: String,
        email: String,
        displayName: String,
    ) {
        handler.launchUnit(
            state = _registerState,
            operation = "register",
            endpoint = "POST /auth/register",
            entryMessage = "register attempt for username=$username",
            block = {
                apiClient.httpClient.post("/auth/register") {
                    setBody(RegisterRequest(username, password, email, displayName))
                }
            },
        )
    }

    fun logout() {
        _loginState.value = UiState.Idle
        handler.launchUnit(
            state = _logoutState,
            operation = "logout",
            endpoint = "POST /api/auth/logout",
            entryMessage = "logout attempt start",
            block = { apiClient.httpClient.post("/api/auth/logout") },
        )
    }
}
