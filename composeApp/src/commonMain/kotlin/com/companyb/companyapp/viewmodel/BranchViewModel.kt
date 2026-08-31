package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.SetRateRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BranchViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "BranchVM")
    private val handleApiError: suspend (HttpResponse, (UiState.Error) -> Unit) -> Boolean = { response, setError ->
        val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
        if (detail == null) {
            false
        } else {
            setError(UiState.Error(detail))
            true
        }
    }

    private val _branchDetail = MutableStateFlow<UiState<BranchResponse>>(UiState.Idle)
    val branchDetail: StateFlow<UiState<BranchResponse>> = _branchDetail.asStateFlow()

    private val _createBranchState = MutableStateFlow<UiState<BranchResponse>>(UiState.Idle)
    val createBranchState: StateFlow<UiState<BranchResponse>> = _createBranchState.asStateFlow()

    private val _assignments = MutableStateFlow<UiState<List<AssignmentResponse>>>(UiState.Idle)
    val assignments: StateFlow<UiState<List<AssignmentResponse>>> = _assignments.asStateFlow()

    private val _assignmentResult = MutableStateFlow<UiState<AssignmentResponse>>(UiState.Idle)
    val assignmentResult: StateFlow<UiState<AssignmentResponse>> = _assignmentResult.asStateFlow()

    private val _deleteAssignmentState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteAssignmentState: StateFlow<UiState<Unit>> = _deleteAssignmentState.asStateFlow()

    private val _slotSwapState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val slotSwapState: StateFlow<UiState<Unit>> = _slotSwapState.asStateFlow()

    private val _rates = MutableStateFlow<UiState<List<RateResponse>>>(UiState.Idle)
    val rates: StateFlow<UiState<List<RateResponse>>> = _rates.asStateFlow()

    private val _setRateState = MutableStateFlow<UiState<RateResponse>>(UiState.Idle)
    val setRateState: StateFlow<UiState<RateResponse>> = _setRateState.asStateFlow()

    fun loadBranchDetail(branchId: String) {
        handler.launch(
            state = _branchDetail,
            operation = "loadBranchDetail",
            endpoint = "GET /api/branches/$branchId",
            block = { apiClient.httpClient.get(ApiRoutes.branch(branchId)) },
            transform = { it.body() },
        )
    }

    fun createBranch(request: CreateBranchRequest) {
        if (_createBranchState.value is UiState.Loading) return
        _createBranchState.value = UiState.Loading
        handler.launch(
            state = _createBranchState,
            operation = "createBranch",
            endpoint = "POST /api/branches",
            block = {
                apiClient.httpClient.post(ApiRoutes.BRANCHES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleApiError(response) { _createBranchState.value = it }
            },
        )
    }

    fun resetAdministrationState() {
        if (_createBranchState.value !is UiState.Loading) {
            _createBranchState.value = UiState.Idle
        }
        if (_assignmentResult.value !is UiState.Loading) {
            _assignmentResult.value = UiState.Idle
        }
        if (_deleteAssignmentState.value !is UiState.Loading) {
            _deleteAssignmentState.value = UiState.Idle
        }
    }

    fun createAssignment(
        branchId: String,
        request: CreateAssignmentRequest,
    ) {
        if (_assignmentResult.value is UiState.Loading) return
        _assignmentResult.value = UiState.Loading
        handler.launch(
            state = _assignmentResult,
            operation = "createAssignment",
            endpoint = "POST /api/branches/$branchId/assignments",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchAssignments(branchId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleApiError(response) { _assignmentResult.value = it }
            },
        )
    }

    fun deleteAssignment(
        branchId: String,
        userId: String,
    ) {
        if (_deleteAssignmentState.value is UiState.Loading) return
        _deleteAssignmentState.value = UiState.Loading
        handler.launchUnit(
            state = _deleteAssignmentState,
            operation = "deleteAssignment",
            endpoint = "DELETE /api/branches/$branchId/assignments/$userId",
            block = {
                apiClient.httpClient.delete(
                    ApiRoutes.branchAssignment(branchId, userId),
                )
            },
            onNonSuccess = { response ->
                handleApiError(response) { _deleteAssignmentState.value = it }
            },
        )
    }

    fun loadAssignments(branchId: String) {
        handler.launch(
            state = _assignments,
            operation = "loadAssignments",
            endpoint = "GET /api/branches/$branchId/assignments",
            block = { apiClient.httpClient.get(ApiRoutes.branchAssignments(branchId)) },
            transform = { it.body() },
        )
    }

    fun swapSlots(
        branchId: String,
        request: SwapSlotsRequest,
    ) {
        handler.launchUnit(
            state = _slotSwapState,
            operation = "swapSlots",
            endpoint = "POST /api/branches/$branchId/slots/swap",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchSlotsSwap(branchId)) {
                    setBody(request)
                }
            },
        )
    }

    fun updateSlot(
        branchId: String,
        userId: String,
        request: UpdateSlotRequest,
    ) {
        if (_assignmentResult.value is UiState.Loading) return
        _assignmentResult.value = UiState.Loading
        handler.launch(
            state = _assignmentResult,
            operation = "updateSlot",
            endpoint = "PATCH /api/branches/$branchId/assignments/$userId/slot",
            block = {
                apiClient.httpClient.patch(
                    ApiRoutes.branchAssignmentSlot(branchId, userId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

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
        if (_setRateState.value is UiState.Loading) return
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
