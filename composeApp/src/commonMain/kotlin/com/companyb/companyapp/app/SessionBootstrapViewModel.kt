package com.companyb.companyapp.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.workforce.ActiveAttendanceResponse
import com.companyb.companyapp.contracts.workforce.ActiveShiftResponse
import com.companyb.companyapp.network.ApiClient
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
 * clock-in publishes the clock context via [BranchSelectViewModel]).
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

    /**
     * #669 — shift-resume outcome: Loading while GET /api/me/active-attendance (+ the
     * post-restore capability refresh) is in flight, Success with the restored shift or
     * null when no shift stands open, Error when the resume leg failed ("Could not
     * restore your shift" — credentials survive, the splash offers Retry).
     */
    private val _restoreState = MutableStateFlow<UiState<ActiveShiftResponse?>>(UiState.Idle)
    val restoreState: StateFlow<UiState<ActiveShiftResponse?>> = _restoreState.asStateFlow()

    private var validationJob: Job? = null

    /** Cancels an in-flight validation (the splash's "Go to Login" discards the attempt). */
    fun cancelValidation() {
        validationJob?.cancel()
        _validationState.value = UiState.Idle
        _restoreState.value = UiState.Idle
    }

    fun validateSession(): Job {
        if (_validationState.value is UiState.Loading) return Job()
        // Synchronous pre-set: the guard must hold from the caller's frame, not after the
        // launched coroutine first runs (double-tap before any dispatch would otherwise
        // launch two validations).
        _validationState.value = UiState.Loading
        _restoreState.value = UiState.Idle
        validationJob =
            handler.launch(
                LaunchRequest(
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
                                AppSessionState.setBootstrapState(me, capabilities)
                            }

                            // 401 on the capabilities leg is the same session-401 class as on the me
                            // leg — the global onUnauthorized handler clears the token (silent at
                            // launch, notice+navigate mid-session). Silent here too: surfacing
                            // UiState.Error would mislabel an auth failure as a connection problem.
                            capabilitiesResponse.status == HttpStatusCode.Unauthorized -> {
                                AppSessionState.clear()
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
                        resolveRestoredShift(me)
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
                ),
            )
        return validationJob!!
    }

    /**
     * #669 — shift-resume leg shared by launch validation and fresh login (both flow
     * through [validateSession]): GET /api/me/active-attendance (read-only — never POSTs
     * a clock-in), then publishes the confirmed clock context plus the ADR-0021
     * post-restore capability refresh. A null shift is the normal no-shift path.
     */
    private suspend fun resolveRestoredShift(me: MeResponse) {
        _restoreState.value = UiState.Loading
        logInfo("SessionBootstrapVM", "GET /api/me/active-attendance (shift-resume trigger)")
        val activeResponse = apiClient.httpClient.get(ApiRoutes.ME_ACTIVE_ATTENDANCE)
        if (activeResponse.status.isSuccess()) {
            val shift = activeResponse.body<ActiveAttendanceResponse>().shift
            publishRestoredShift(me, shift)
            _restoreState.value = UiState.Success(shift)
            return
        }
        if (activeResponse.status == HttpStatusCode.Unauthorized) {
            AppSessionState.clear()
            _validationState.value = UiState.Idle
            _restoreState.value = UiState.Idle
            throw CancellationException("Session invalidated during shift restore")
        }
        _restoreState.value = UiState.Error("Could not restore your shift")
        error("active-attendance fetch failed: ${activeResponse.status.value}")
    }

    private suspend fun publishRestoredShift(
        me: MeResponse,
        shift: ActiveShiftResponse?,
    ) {
        if (shift == null) return
        // Stale-publication guard: a logout/account replacement while the reads were in
        // flight must not publish a dead session's clock.
        if (AppSessionState.snapshot.value.user
                ?.id != me.id
        ) {
            return
        }
        AppSessionState.setRestoredShift(shift)
        logInfo("SessionBootstrapVM", "GET /api/me/capabilities (post-restore trigger)")
        val refresh = apiClient.httpClient.get(ApiRoutes.ME_CAPABILITIES)
        if (refresh.status.isSuccess()) {
            if (AppSessionState.snapshot.value.clock
                    ?.attendanceId == shift.attendanceId &&
                AppSessionState.snapshot.value.user
                    ?.id == me.id
            ) {
                AppSessionState.setCapabilities(refresh.body())
            }
            return
        }
        if (refresh.status == HttpStatusCode.Unauthorized) {
            AppSessionState.clear()
            _validationState.value = UiState.Idle
            _restoreState.value = UiState.Idle
            throw CancellationException("Session invalidated during post-restore refresh")
        }
        _restoreState.value = UiState.Error("Could not restore your shift")
        error("post-restore capabilities fetch failed: ${refresh.status.value}")
    }
}
