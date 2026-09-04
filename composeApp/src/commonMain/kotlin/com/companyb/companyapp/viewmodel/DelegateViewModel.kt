package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DelegateViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "DelegateVM")
    private val handleApiError: suspend (HttpResponse, (UiState.Error) -> Unit) -> Boolean = { response, setError ->
        val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
        if (detail == null) {
            false
        } else {
            setError(UiState.Error(detail))
            true
        }
    }

    private val _assignResult = MutableStateFlow<UiState<DelegateResponse>>(UiState.Idle)
    val assignResult: StateFlow<UiState<DelegateResponse>> = _assignResult.asStateFlow()

    private val _delegates = MutableStateFlow<UiState<List<DelegateResponse>>>(UiState.Idle)
    val delegates: StateFlow<UiState<List<DelegateResponse>>> = _delegates.asStateFlow()

    private val _revokeResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val revokeResult: StateFlow<UiState<Unit>> = _revokeResult.asStateFlow()

    private var loadedBranchId: String? = null
    private var loadJob: Job? = null
    private var delegateGeneration = 0L

    fun loadDelegates(
        branchId: String,
        force: Boolean = false,
    ) {
        if (!force && loadedBranchId == branchId && _delegates.value is UiState.Loading) return
        loadJob?.cancel()
        loadedBranchId = branchId
        val generation = ++delegateGeneration
        _delegates.value = UiState.Loading
        loadJob =
            handler.launchStateless(
                operation = "loadDelegates",
                endpoint = "GET /api/branches/$branchId/delegates",
                block = { apiClient.httpClient.get(ApiRoutes.branchDelegates(branchId)) },
                transform = { response ->
                    val rows = response.body<List<DelegateResponse>>()
                    if (generation == delegateGeneration) {
                        _delegates.value = UiState.Success(rows)
                    }
                },
                hooks =
                    StatelessHooks(
                        onNonSuccess = { response ->
                            val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                            if (generation == delegateGeneration) {
                                _delegates.value =
                                    UiState.Error(detail ?: "loadDelegates failed: ${response.status.value}")
                            }
                        },
                        onError = { error ->
                            if (generation == delegateGeneration) {
                                _delegates.value = UiState.Error(error.message ?: "Unknown error")
                            }
                        },
                        stale = { generation != delegateGeneration },
                    ),
            )
    }

    fun clearDelegates() {
        loadJob?.cancel()
        ++delegateGeneration
        loadedBranchId = null
        _delegates.value = UiState.Idle
    }

    fun clearMutationResults() {
        if (_assignResult.value !is UiState.Loading) _assignResult.value = UiState.Idle
        if (_revokeResult.value !is UiState.Loading) _revokeResult.value = UiState.Idle
    }

    fun assignDelegate(request: AssignDelegateRequest) {
        if (_assignResult.value is UiState.Loading) return
        _assignResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = _assignResult,
                operation = "assignDelegate",
                endpoint = "POST /api/delegates",
                block = {
                    apiClient.httpClient.post(ApiRoutes.DELEGATES) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
                onNonSuccess = { response ->
                    handleApiError(response) { _assignResult.value = it }
                },
            ),
        )
    }

    fun revokeDelegate(delegateId: String) {
        if (_revokeResult.value is UiState.Loading) return
        _revokeResult.value = UiState.Loading
        handler.launchUnit(
            state = _revokeResult,
            operation = "revokeDelegate",
            endpoint = "DELETE /api/delegates/$delegateId",
            block = { apiClient.httpClient.delete(ApiRoutes.delegate(delegateId)) },
            hooks =
                LaunchHooks(
                    onNonSuccess = { response ->
                        handleApiError(response) { _revokeResult.value = it }
                    },
                ),
        )
    }
}
