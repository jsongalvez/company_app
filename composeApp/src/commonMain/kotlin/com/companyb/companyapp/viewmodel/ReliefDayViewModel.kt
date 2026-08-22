package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #358 — the relief deep-link destination: a branch day's request rows, addressed by
 * (branchId, date) exactly as a notification carries them. Bearer + row scoping on the
 * backend (members see every request, outsiders their own) — no capability gate client-side.
 */
class ReliefDayViewModel(
    private val apiClient: ApiClient,
    private val branchId: String,
    private val date: String,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReliefDayVM")

    private val _requests = MutableStateFlow<UiState<List<ReliefAccessResponse>>>(UiState.Idle)
    val requests: StateFlow<UiState<List<ReliefAccessResponse>>> = _requests.asStateFlow()

    fun load() {
        handler.launch(
            state = _requests,
            operation = "loadReliefDay",
            endpoint = "GET /api/relief-access?branchId&date",
            block = { apiClient.httpClient.get(ApiRoutes.reliefAccessByBranchAndDate(branchId, date)) },
            transform = { it.body() },
        )
    }
}
