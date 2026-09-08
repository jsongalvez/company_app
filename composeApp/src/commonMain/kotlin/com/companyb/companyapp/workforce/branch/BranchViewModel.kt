package com.companyb.companyapp.workforce.branch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LaunchHooks
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.CreateBranchRequest
import com.companyb.companyapp.contracts.workforce.AssignmentResponse
import com.companyb.companyapp.contracts.workforce.CreateAssignmentRequest
import com.companyb.companyapp.contracts.workforce.UpdateSlotRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.extractApiErrorMessage
import com.companyb.companyapp.workforce.AssignmentSlotOperations
import io.ktor.client.call.body
import io.ktor.client.request.delete
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

    private val _createBranchState = MutableStateFlow<UiState<BranchResponse>>(UiState.Idle)
    val createBranchState: StateFlow<UiState<BranchResponse>> = _createBranchState.asStateFlow()

    private val _assignmentResult = MutableStateFlow<UiState<AssignmentResponse>>(UiState.Idle)
    val assignmentResult: StateFlow<UiState<AssignmentResponse>> = _assignmentResult.asStateFlow()

    private val _slotUpdate = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val slotUpdate: StateFlow<UiState<Unit>> = _slotUpdate.asStateFlow()

    private val _deleteAssignmentState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteAssignmentState: StateFlow<UiState<Unit>> = _deleteAssignmentState.asStateFlow()

    fun createBranch(request: CreateBranchRequest) {
        if (_createBranchState.value is UiState.Loading) return
        _createBranchState.value = UiState.Loading
        handler.launch(
            LaunchRequest(
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
            ),
        )
    }

    fun resetAdministrationState() {
        if (_createBranchState.value !is UiState.Loading) {
            _createBranchState.value = UiState.Idle
        }
        if (_assignmentResult.value !is UiState.Loading) {
            _assignmentResult.value = UiState.Idle
        }
        if (_slotUpdate.value !is UiState.Loading) {
            _slotUpdate.value = UiState.Idle
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
            LaunchRequest(
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
            ),
        )
    }

    fun deleteAssignment(
        branchId: String,
        assignmentId: String,
    ) {
        if (_deleteAssignmentState.value is UiState.Loading) return
        _deleteAssignmentState.value = UiState.Loading
        handler.launchUnit(
            state = _deleteAssignmentState,
            operation = "deleteAssignment",
            endpoint = "DELETE /api/branches/$branchId/assignments/$assignmentId",
            block = {
                apiClient.httpClient.delete(
                    ApiRoutes.branchAssignment(branchId, assignmentId),
                )
            },
            hooks =
                LaunchHooks(
                    onNonSuccess = { response ->
                        handleApiError(response) { _deleteAssignmentState.value = it }
                    },
                ),
        )
    }

    fun updateSlot(
        branchId: String,
        assignmentId: String,
        request: UpdateSlotRequest,
    ) {
        if (_slotUpdate.value is UiState.Loading) return
        _slotUpdate.value = UiState.Loading
        handler.launchUnit(
            state = _slotUpdate,
            operation = "updateSlot",
            endpoint = "PATCH /api/branches/$branchId/assignments/$assignmentId/slot",
            block = {
                AssignmentSlotOperations.updateSlot(apiClient, branchId, assignmentId, request.slot)
            },
            hooks =
                LaunchHooks(
                    onNonSuccess = { response ->
                        handleApiError(response) { _slotUpdate.value = it }
                    },
                ),
        )
    }
}
