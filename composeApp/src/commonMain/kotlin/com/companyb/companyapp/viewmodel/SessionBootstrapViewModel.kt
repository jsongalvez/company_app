package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
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
 * GET /api/me/capabilities → publish both values to SessionState (the FULL row list — #156;
 * ADR-0021's fetch timing is unchanged: branch-scoped resolution just fails closed until
 * clock-in sets selectedBranchId via [BranchSelectViewModel]).
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

    private var validationJob: Job? = null

    /** Cancels an in-flight validation (the splash's "Go to Login" discards the attempt). */
    fun cancelValidation() {
        validationJob?.cancel()
        _validationState.value = UiState.Idle
    }

    fun validateSession(): Job {
        if (_validationState.value is UiState.Loading) return Job()
        // Synchronous pre-set: the guard must hold from the caller's frame, not after the
        // launched coroutine first runs (double-tap before any dispatch would otherwise
        // launch two validations).
        _validationState.value = UiState.Loading
        validationJob =
            handler.launch(
                state = _validationState,
                operation = "validateSession",
                endpoint = "GET /api/me",
                entryMessage = "validateSession called (token present)",
                block = { apiClient.httpClient.get(ApiRoutes.ME) },
                transform = { response ->
                    val me = response.body<MeResponse>()
                    logInfo("SessionBootstrapVM", "GET /api/me/capabilities (post-login/launch trigger)")
                    val capabilitiesResponse = apiClient.httpClient.get(ApiRoutes.ME_CAPABILITIES)
                    when {
                        capabilitiesResponse.status.isSuccess() -> {
                            val capabilities = capabilitiesResponse.body<List<UserCapabilityResponse>>()
                            SessionState.setBootstrapState(me, capabilities)
                        }

                        // 401 on the capabilities leg is the same session-401 class as on the me
                        // leg — the global onUnauthorized handler clears the token (silent at
                        // launch, notice+navigate mid-session). Silent here too: surfacing
                        // UiState.Error would mislabel an auth failure as a connection problem.
                        capabilitiesResponse.status == HttpStatusCode.Unauthorized -> {
                            SessionState.clear()
                            _validationState.value = UiState.Idle
                            throw CancellationException("Session invalidated during capabilities validation")
                        }

                        // Any other failure is a genuine validation failure — Error (the splash
                        // keeps the token and offers Retry; LoginScreen shows the bootstrap copy).
                        else -> {
                            error(
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
                        // presence, not this state). Idle, not a lying stuck-Loading — a stuck
                        // Loading would keep LoginScreen's form disabled until re-composition.
                        _validationState.value = UiState.Idle
                        true
                    } else {
                        false
                    }
                },
            )
        return validationJob!!
    }
}
