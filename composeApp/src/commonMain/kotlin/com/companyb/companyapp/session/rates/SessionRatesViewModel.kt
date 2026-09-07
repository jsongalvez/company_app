package com.companyb.companyapp.session.rates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SetRateRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #573 — session base-rate administration state owner, extracted from
 * workforce `BranchViewModel` (which retains branch and membership
 * administration). Owns only the rate list and the single in-flight save;
 * the screen reloads authoritatively on every terminal save (ADR-0022).
 */
class SessionRatesViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionRatesVM")

    private val _rates = MutableStateFlow<UiState<List<RateResponse>>>(UiState.Idle)
    val rates: StateFlow<UiState<List<RateResponse>>> = _rates.asStateFlow()

    private val _setRateState = MutableStateFlow<UiState<RateResponse>>(UiState.Idle)
    val setRateState: StateFlow<UiState<RateResponse>> = _setRateState.asStateFlow()

    fun loadRates(branchId: String) {
        handler.launch(
            state = _rates,
            operation = "loadRates",
            endpoint = "GET /api/branches/$branchId/rates",
            block = { apiClient.httpClient.get(ApiRoutes.branchRates(branchId)) },
            transform = { it.body() },
        )
    }

    fun setRate(
        branchId: String,
        request: SetRateRequest,
    ) {
        // Single-flight: one in-flight save at a time, so a double-tap before recomposition
        // cannot mint two rotation ids (the second would close the first's row immediately).
        // The Loading preset is synchronous (the createBranch pattern): the handler assigns
        // Loading inside its coroutine, which is not guaranteed to land before launch returns.
        if (_setRateState.value is UiState.Loading) return
        _setRateState.value = UiState.Loading
        handler.launch(
            state = _setRateState,
            operation = "setRate",
            endpoint = "POST /api/branches/$branchId/rates",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchRates(branchId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
