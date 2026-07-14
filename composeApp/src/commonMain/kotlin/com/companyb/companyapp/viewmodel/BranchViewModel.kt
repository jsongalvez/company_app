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
        viewModelScope.launch {
            _branches.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/branches")
                if (response.status.isSuccess()) {
                    _branches.value = UiState.Success(response.body())
                } else {
                    _branches.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _branches.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadBranchDetail(branchId: String) {
        viewModelScope.launch {
            _branchDetail.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/branches/$branchId")
                if (response.status.isSuccess()) {
                    _branchDetail.value = UiState.Success(response.body())
                } else {
                    _branchDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _branchDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createBranch(request: CreateBranchRequest) {
        viewModelScope.launch {
            _createBranchState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/branches") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createBranchState.value = UiState.Success(response.body())
                } else {
                    _createBranchState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createBranchState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadAssignments(branchId: String) {
        viewModelScope.launch {
            _assignments.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/branches/$branchId/assignments")
                if (response.status.isSuccess()) {
                    _assignments.value = UiState.Success(response.body())
                } else {
                    _assignments.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _assignments.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createAssignment(
        branchId: String,
        request: CreateAssignmentRequest,
    ) {
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/assignments") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _assignmentResult.value = UiState.Success(response.body())
                } else {
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteAssignment(
        branchId: String,
        userId: String,
    ) {
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.delete(
                        "/api/branches/$branchId/assignments/$userId",
                    )
                if (response.status.isSuccess()) {
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
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun swapSlots(
        branchId: String,
        request: SwapSlotsRequest,
    ) {
        viewModelScope.launch {
            _slotSwapState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/slots/swap") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _slotSwapState.value = UiState.Success(Unit)
                } else {
                    _slotSwapState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _slotSwapState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateSlot(
        branchId: String,
        userId: String,
        request: UpdateSlotRequest,
    ) {
        viewModelScope.launch {
            _assignmentResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch(
                        "/api/branches/$branchId/assignments/$userId/slot",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _assignmentResult.value = UiState.Success(response.body())
                } else {
                    _assignmentResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _assignmentResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadRates(branchId: String) {
        viewModelScope.launch {
            _rates.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/branches/$branchId/rates")
                if (response.status.isSuccess()) {
                    _rates.value = UiState.Success(response.body())
                } else {
                    _rates.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _rates.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setRate(
        branchId: String,
        request: SetRateRequest,
    ) {
        viewModelScope.launch {
            _setRateState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/rates") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _setRateState.value = UiState.Success(response.body())
                } else {
                    _setRateState.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _setRateState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
