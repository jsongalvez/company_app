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
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _loginState = MutableStateFlow<UiState<LoginResponse>>(UiState.Idle)
    val loginState: StateFlow<UiState<LoginResponse>> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val registerState: StateFlow<UiState<Unit>> = _registerState.asStateFlow()

    private val _logoutState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val logoutState: StateFlow<UiState<Unit>> = _logoutState.asStateFlow()

    fun login(
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/auth/login") {
                        setBody(LoginRequest(username, password))
                    }
                if (response.status.isSuccess()) {
                    val body = response.body<LoginResponse>()
                    _loginState.value = UiState.Success(body)
                } else {
                    _loginState.value = UiState.Error("Login failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _loginState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun register(
        username: String,
        password: String,
        email: String,
        displayName: String,
    ) {
        viewModelScope.launch {
            _registerState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/auth/register") {
                        setBody(RegisterRequest(username, password, email, displayName))
                    }
                if (response.status.isSuccess()) {
                    _registerState.value = UiState.Success(Unit)
                } else {
                    _registerState.value = UiState.Error("Register failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _registerState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _loginState.value = UiState.Idle
            _logoutState.value = UiState.Loading
            try {
                val response = apiClient.httpClient.post("/api/auth/logout")
                if (response.status.isSuccess()) {
                    _logoutState.value = UiState.Success(Unit)
                } else {
                    _logoutState.value = UiState.Error("Logout failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _logoutState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
