package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CreateAssignmentRequest
import com.companyb.companyapp.dto.CreateBranchRequest
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.SetRateRequest
import com.companyb.companyapp.dto.SwapSlotsRequest
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BranchViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _branches = MutableStateFlow<UiState<List<BranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<BranchResponse>>> = _branches.asStateFlow()

    private val _branchDetail = MutableStateFlow<UiState<BranchResponse>>(UiState.Idle)
    val branchDetail: StateFlow<UiState<BranchResponse>> = _branchDetail.asStateFlow()

    private val _createBranchState = MutableStateFlow<UiState<BranchResponse>>(UiState.Idle)
    val createBranchState: StateFlow<UiState<BranchResponse>> = _createBranchState.asStateFlow()

    private val _assignments = MutableStateFlow<UiState<List<AssignmentResponse>>>(UiState.Idle)
    val assignments: StateFlow<UiState<List<AssignmentResponse>>> = _assignments.asStateFlow()

    private val _assignmentResult = MutableStateFlow<UiState<AssignmentResponse>>(UiState.Idle)
    val assignmentResult: StateFlow<UiState<AssignmentResponse>> = _assignmentResult.asStateFlow()

    private val _slotSwapState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val slotSwapState: StateFlow<UiState<Unit>> = _slotSwapState.asStateFlow()

    private val _rates = MutableStateFlow<UiState<List<RateResponse>>>(UiState.Idle)
    val rates: StateFlow<UiState<List<RateResponse>>> = _rates.asStateFlow()

    private val _setRateState = MutableStateFlow<UiState<RateResponse>>(UiState.Idle)
    val setRateState: StateFlow<UiState<RateResponse>> = _setRateState.asStateFlow()

    fun loadBranches() {
        logInfo("BranchVM", "loadBranches called")
        viewModelScope.launch {
            _branches.value = UiState.Loading
            try {
                logInfo("BranchVM", "GET /api/branches")
                val response = apiClient.httpClient.get("/api/branches")
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "loadBranches success")
                    _branches.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "loadBranches failed: status=${response.status.value}")
                    _branches.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "loadBranches exception", e)
                _branches.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadBranchDetail(branchId: String) {
        logInfo("BranchVM", "loadBranchDetail called: branchId=$branchId")
        viewModelScope.launch {
            _branchDetail.value = UiState.Loading
            try {
                logInfo("BranchVM", "GET /api/branches/$branchId")
                val response = apiClient.httpClient.get("/api/branches/$branchId")
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "loadBranchDetail success")
                    _branchDetail.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "loadBranchDetail failed: status=${response.status.value}")
                    _branchDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "loadBranchDetail exception", e)
                _branchDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createBranch(request: CreateBranchRequest) {
        logInfo("BranchVM", "createBranch called")
        viewModelScope.launch {
            _createBranchState.value = UiState.Loading
            try {
                logInfo("BranchVM", "POST /api/branches")
                val response =
                    apiClient.httpClient.post("/api/branches") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "createBranch success")
                    _createBranchState.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "createBranch failed: status=${response.status.value}")
                    _createBranchState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "createBranch exception", e)
                _createBranchState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadAssignments(branchId: String) {
        logInfo("BranchVM", "loadAssignments called: branchId=$branchId")
        viewModelScope.launch {
            _assignments.value = UiState.Loading
            try {
                logInfo("BranchVM", "GET /api/branches/$branchId/assignments")
                val response = apiClient.httpClient.get("/api/branches/$branchId/assignments")
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "loadAssignments success")
                    _assignments.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "loadAssignments failed: status=${response.status.value}")
                    _assignments.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "loadAssignments exception", e)
                _assignments.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createAssignment(
        branchId: String,
        request: CreateAssignmentRequest,
    ) {
        logInfo("BranchVM", "createAssignment called: branchId=$branchId")
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                logInfo("BranchVM", "POST /api/branches/$branchId/assignments")
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/assignments") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "createAssignment success")
                    _assignmentResult.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "createAssignment failed: status=${response.status.value}")
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "createAssignment exception", e)
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteAssignment(
        branchId: String,
        userId: String,
    ) {
        logInfo("BranchVM", "deleteAssignment called: branchId=$branchId, userId=$userId")
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                logInfo("BranchVM", "DELETE /api/branches/$branchId/assignments/$userId")
                val response =
                    apiClient.httpClient.delete(
                        "/api/branches/$branchId/assignments/$userId",
                    )
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "deleteAssignment success")
                    _assignmentResult.value =
                        UiState.Success(
                            AssignmentResponse(
                                id = "",
                                userId = userId,
                                branchId = branchId,
                                slot = 0,
                                assignedBy = "",
                                assignedAt = "",
                            ),
                        )
                } else {
                    logInfo("BranchVM", "deleteAssignment failed: status=${response.status.value}")
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "deleteAssignment exception", e)
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun swapSlots(
        branchId: String,
        request: SwapSlotsRequest,
    ) {
        logInfo("BranchVM", "swapSlots called: branchId=$branchId")
        viewModelScope.launch {
            _slotSwapState.value = UiState.Loading
            try {
                logInfo("BranchVM", "POST /api/branches/$branchId/slots/swap")
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/slots/swap") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "swapSlots success")
                    _slotSwapState.value = UiState.Success(Unit)
                } else {
                    logInfo("BranchVM", "swapSlots failed: status=${response.status.value}")
                    _slotSwapState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "swapSlots exception", e)
                _slotSwapState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateSlot(
        branchId: String,
        userId: String,
        request: UpdateSlotRequest,
    ) {
        logInfo("BranchVM", "updateSlot called: branchId=$branchId, userId=$userId")
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                logInfo("BranchVM", "PATCH /api/branches/$branchId/assignments/$userId/slot")
                val response =
                    apiClient.httpClient.patch(
                        "/api/branches/$branchId/assignments/$userId/slot",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "updateSlot success")
                    _assignmentResult.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "updateSlot failed: status=${response.status.value}")
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "updateSlot exception", e)
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadRates(branchId: String) {
        logInfo("BranchVM", "loadRates called: branchId=$branchId")
        viewModelScope.launch {
            _rates.value = UiState.Loading
            try {
                logInfo("BranchVM", "GET /api/branches/$branchId/rates")
                val response = apiClient.httpClient.get("/api/branches/$branchId/rates")
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "loadRates success")
                    _rates.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "loadRates failed: status=${response.status.value}")
                    _rates.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "loadRates exception", e)
                _rates.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setRate(
        branchId: String,
        request: SetRateRequest,
    ) {
        logInfo("BranchVM", "setRate called: branchId=$branchId")
        viewModelScope.launch {
            _setRateState.value = UiState.Loading
            try {
                logInfo("BranchVM", "POST /api/branches/$branchId/rates")
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/rates") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("BranchVM", "setRate success")
                    _setRateState.value = UiState.Success(response.body())
                } else {
                    logInfo("BranchVM", "setRate failed: status=${response.status.value}")
                    _setRateState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("BranchVM", "setRate exception", e)
                _setRateState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
