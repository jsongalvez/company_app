package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.globalCapabilities
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #94 — the shared session-bootstrap implementation used identically by launch validation
 * (App.kt splash, Phase 1) and fresh login (LoginScreen, Phase 2): GET /api/me →
 * SessionState.setUser → GET /api/me/capabilities → SessionState.setCapabilities (global
 * slice only — ADR-0021; the branch-scoped slice resolves at clock-in via
 * [BranchSelectViewModel]).
 *
 * A 401 during validation is deliberately NOT surfaced as UiState.Error: ApiClient's global
 * onUnauthorized flow has already cleared the token (App.kt), which is what transitions the
 * UI (splash → Login). Network errors DO surface as Error — the splash keeps the token and
 * offers Retry (#94 Q3b(ii) key fork: "backend couldn't say" ≠ "said no").
 */
class SessionBootstrapViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionBootstrapVM")

    private val _validationState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val validationState: StateFlow<UiState<Unit>> = _validationState.asStateFlow()

    fun validateSession(): Job {
        if (_validationState.value is UiState.Loading) return Job()
        // Synchronous pre-set: the guard must hold from the caller's frame, not after the
        // launched coroutine first runs (double-tap before any dispatch would otherwise
        // launch two validations).
        _validationState.value = UiState.Loading
        return handler.launch(
            state = _validationState,
            operation = "validateSession",
            endpoint = "GET /api/me",
            entryMessage = "validateSession called (token present)",
            block = { apiClient.httpClient.get("/api/me") },
            transform = { response ->
                val me = response.body<MeResponse>()
                SessionState.setUser(me)
                logInfo("SessionBootstrapVM", "GET /api/me/capabilities (post-login/launch trigger)")
                val capabilitiesResponse = apiClient.httpClient.get("/api/me/capabilities")
                when {
                    capabilitiesResponse.status.isSuccess() -> {
                        val capabilities = capabilitiesResponse.body<List<UserCapabilityResponse>>()
                        SessionState.setCapabilities(globalCapabilities(capabilities))
                    }

                    // 401 on the capabilities leg is the same session-401 class as on the me
                    // leg — the global onUnauthorized handler clears the token (silent at
                    // launch, notice+navigate mid-session). Silent here too: surfacing
                    // UiState.Error would mislabel an auth failure as a connection problem.
                    capabilitiesResponse.status == HttpStatusCode.Unauthorized -> {
                        Unit
                    }

                    // Any other failure is a genuine validation failure — Error (the splash
                    // keeps the token and offers Retry; LoginScreen shows the bootstrap copy).
                    else -> {
                        throw IllegalStateException(
                            "capabilities fetch failed: ${capabilitiesResponse.status.value}",
                        )
                    }
                }
                Unit
            },
            onNonSuccess = { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    logWarn(
                        "SessionBootstrapVM",
                        "validateSession 401 — token invalid, App-level handler clears it",
                    )
                    // Fully handled: no UiState.Error (the splash derives from token
                    // presence, not this state — see class KDoc).
                    true
                } else {
                    false
                }
            },
        )
    }
}
