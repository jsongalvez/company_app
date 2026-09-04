package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AcceptInviteRequest
import com.companyb.companyapp.dto.ForgotPasswordRequest
import com.companyb.companyapp.dto.LoginRequest
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.dto.ResetPasswordRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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

    private val _logoutState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val logoutState: StateFlow<UiState<Unit>> = _logoutState.asStateFlow()

    // #350 — accept-invite result; Success means the password is set and the code consumed.
    private val _acceptInviteState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val acceptInviteState: StateFlow<UiState<Unit>> = _acceptInviteState.asStateFlow()

    fun login(
        username: String,
        password: String,
    ): Job =
        handler.launch(
            LaunchRequest(
                state = _loginState,
                operation = "login",
                endpoint = "POST ${ApiRoutes.AUTH_LOGIN}",
                entryMessage = "login attempt for username=$username",
                block = {
                    apiClient.httpClient.post(ApiRoutes.AUTH_LOGIN) {
                        setBody(LoginRequest(username, password))
                    }
                },
                transform = { it.body() },
            ),
        )

    fun logout() {
        _loginState.value = UiState.Idle
        handler.launchUnit(
            state = _logoutState,
            operation = "logout",
            endpoint = "POST /api/auth/logout",
            block = { apiClient.httpClient.post(ApiRoutes.AUTH_LOGOUT) },
            hooks =
                LaunchHooks(
                    entryMessage = "logout attempt start",
                ),
        )
    }

    /**
     * #350 — public single-use invite redemption. 204 (no body) lands Success; the backend's
     * 400 `{"error": ...}` body names the failure class (invalid / used / expired / weak
     * password) and renders inline.
     */
    fun acceptInvite(
        token: String,
        newPassword: String,
    ): Job =
        handler.launch(
            LaunchRequest(
                state = _acceptInviteState,
                operation = "acceptInvite",
                endpoint = "POST ${ApiRoutes.AUTH_ACCEPT_INVITE}",
                block = {
                    apiClient.httpClient.post(ApiRoutes.AUTH_ACCEPT_INVITE) {
                        setBody(AcceptInviteRequest(token, newPassword))
                    }
                },
                transform = { Unit },
                onNonSuccess = { response ->
                    // The backend's 400 body names the failure class (invalid / already used /
                    // expired / weak password) — surface it instead of a bare status.
                    val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                    _acceptInviteState.value =
                        UiState.Error(detail ?: "Accept invite failed: ${response.status.value}")
                    true
                },
            ),
        )

    // #353 — forgot-password request leg. 204 is uniform by design (enumeration resistance):
    // Success never implies the account exists, and the screen copy must not claim it does.
    private val _requestResetState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val requestResetState: StateFlow<UiState<Unit>> = _requestResetState.asStateFlow()

    fun requestPasswordReset(identifier: String): Job =
        handler.launch(
            LaunchRequest(
                state = _requestResetState,
                operation = "requestPasswordReset",
                endpoint = "POST ${ApiRoutes.AUTH_FORGOT_PASSWORD}",
                block = {
                    apiClient.httpClient.post(ApiRoutes.AUTH_FORGOT_PASSWORD) {
                        setBody(ForgotPasswordRequest(identifier))
                    }
                },
                transform = { Unit },
                onNonSuccess = { response ->
                    val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                    _requestResetState.value =
                        UiState.Error(detail ?: "Reset request failed: ${response.status.value}")
                    true
                },
            ),
        )

    // #353 — reset redemption leg; the backend's 400 body names the failure class
    // (invalid / used / expired / weak password) and renders inline.
    private val _resetPasswordState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val resetPasswordState: StateFlow<UiState<Unit>> = _resetPasswordState.asStateFlow()

    fun resetPassword(
        token: String,
        newPassword: String,
    ): Job =
        handler.launch(
            LaunchRequest(
                state = _resetPasswordState,
                operation = "resetPassword",
                endpoint = "POST ${ApiRoutes.AUTH_RESET_PASSWORD}",
                block = {
                    apiClient.httpClient.post(ApiRoutes.AUTH_RESET_PASSWORD) {
                        setBody(ResetPasswordRequest(token, newPassword))
                    }
                },
                transform = { Unit },
                onNonSuccess = { response ->
                    val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                    _resetPasswordState.value =
                        UiState.Error(detail ?: "Password reset failed: ${response.status.value}")
                    true
                },
            ),
        )
}
