package com.companyb.companyapp.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.workforce.ClockOutRequest
import com.companyb.companyapp.contracts.workforce.ClockOutResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AttendanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AttendanceVM")

    private val _clockOutState = MutableStateFlow<UiState<ClockOutResponse>>(UiState.Idle)
    val clockOutState: StateFlow<UiState<ClockOutResponse>> = _clockOutState.asStateFlow()

    fun resetClockOut() {
        // #147 pass-2 — the drawer dialog reopens after a failed attempt: a stale Error must
        // not persist into the fresh attempt's frame (the sync Loading pre-set below would
        // overwrite it, but only AFTER the confirm tap — the dialog would show the old error).
        _clockOutState.value = UiState.Idle
    }

    fun clockOut(request: ClockOutRequest): Job {
        // #147 — mirrors clockIn (#140 r1 pattern): synchronous pre-set + Job return so the
        // drawer's dialog can disable the confirm and chain navigation on success.
        _clockOutState.value = UiState.Loading
        return handler.launch(
            state = _clockOutState,
            operation = "clockOut",
            endpoint = "POST /api/attendance/clock-out",
            block = {
                apiClient.httpClient.post(ApiRoutes.ATTENDANCE_CLOCK_OUT) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
