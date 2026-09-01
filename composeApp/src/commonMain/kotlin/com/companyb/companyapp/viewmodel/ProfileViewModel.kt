package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UpdateSlotRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #381 — the profile/self-service surface VM. Three independent reads (`GET /api/me`,
 * `GET /api/me/branches`, `GET /api/me/capabilities`) plus the self slot edit — the backend's
 * `updateSlot` self-leg authorizes own assignments only, so every assigned row here is
 * editable and no admin affordance exists on this surface.
 */
class ProfileViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ProfileVM")

    private val _me = MutableStateFlow<UiState<MeResponse>>(UiState.Idle)
    val me: StateFlow<UiState<MeResponse>> = _me.asStateFlow()

    private val _branches = MutableStateFlow<UiState<List<MeBranchResponse>>>(UiState.Idle)
    val branches: StateFlow<UiState<List<MeBranchResponse>>> = _branches.asStateFlow()

    private val _capabilities = MutableStateFlow<UiState<List<UserCapabilityResponse>>>(UiState.Idle)
    val capabilities: StateFlow<UiState<List<UserCapabilityResponse>>> = _capabilities.asStateFlow()

    private val _slotUpdate = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val slotUpdate: StateFlow<UiState<Unit>> = _slotUpdate.asStateFlow()

    /** Entry load + error-retry: all three sections re-fetch together (one Retry affordance). */
    fun loadAll() {
        handler.launch(
            state = _me,
            operation = "loadMe",
            endpoint = "GET ${ApiRoutes.ME}",
            block = { apiClient.httpClient.get(ApiRoutes.ME) },
            transform = { it.body() },
        )
        loadBranches()
        handler.launch(
            state = _capabilities,
            operation = "loadCapabilities",
            endpoint = "GET ${ApiRoutes.ME_CAPABILITIES}",
            block = { apiClient.httpClient.get(ApiRoutes.ME_CAPABILITIES) },
            transform = { it.body() },
        )
    }

    fun loadBranches() {
        handler.launch(
            state = _branches,
            operation = "loadBranches",
            endpoint = "GET ${ApiRoutes.ME_BRANCHES}",
            block = { apiClient.httpClient.get(ApiRoutes.ME_BRANCHES) },
            transform = { it.body() },
        )
    }

    /**
     * Self slot edit on an own assignment. The server 4xx body's message surfaces inline
     * ([extractApiErrorMessage]); a 2xx refreshes the branch rows so the new slot shows
     * immediately (no reload round-trip for identity/capabilities — they didn't change).
     */
    fun updateSlot(
        branchId: String,
        assignmentId: String,
        slot: Short,
    ) {
        if (_slotUpdate.value is UiState.Loading) return
        _slotUpdate.value = UiState.Loading
        handler.launch(
            state = _slotUpdate,
            operation = "updateSlot",
            endpoint = "PATCH ${ApiRoutes.branchAssignmentSlot(branchId, assignmentId)}",
            block = {
                apiClient.httpClient.patch(ApiRoutes.branchAssignmentSlot(branchId, assignmentId)) {
                    setBody(UpdateSlotRequest(slot))
                }
            },
            transform = {
                loadBranches()
                Unit
            },
            onNonSuccess = { response ->
                val message =
                    extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                        ?: "Slot update failed: ${response.status.value}"
                _slotUpdate.value = UiState.Error(message)
                true
            },
        )
    }

    /** Clears the slot-edit attempt state so the next dialog opens without a stale error. */
    fun resetSlotUpdate() {
        if (_slotUpdate.value !is UiState.Loading) {
            _slotUpdate.value = UiState.Idle
        }
    }
}
