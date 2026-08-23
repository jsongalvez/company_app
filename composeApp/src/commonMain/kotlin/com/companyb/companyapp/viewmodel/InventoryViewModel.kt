package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InventoryViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "InventoryVM")

    private val _inventory = MutableStateFlow<UiState<List<BranchInventoryResponse>>>(UiState.Idle)
    val inventory: StateFlow<UiState<List<BranchInventoryResponse>>> = _inventory.asStateFlow()

    private val _cardResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val cardResult: StateFlow<UiState<Unit>> = _cardResult.asStateFlow()

    private val _restockResult = MutableStateFlow<UiState<InventoryMovementResponse>>(UiState.Idle)
    val restockResult: StateFlow<UiState<InventoryMovementResponse>> = _restockResult.asStateFlow()

    private val _movementResult = MutableStateFlow<UiState<InventoryMovementResponse>>(UiState.Idle)
    val movementResult: StateFlow<UiState<InventoryMovementResponse>> = _movementResult.asStateFlow()

    fun loadInventory(branchId: String) {
        handler.launch(
            state = _inventory,
            operation = "loadInventory",
            endpoint = "GET /api/branches/$branchId/inventory",
            block = { apiClient.httpClient.get(ApiRoutes.branchInventory(branchId)) },
            transform = { it.body() },
        )
    }

    /**
     * #392 — the route answers 201 with NO body (`BranchInventoryRoutes.handleEnsureCard`
     * calls only `context.status(CREATED)`), so this must not deserialize one: the old
     * `it.body()` shape threw on the first real success. launchUnit owns status-only calls.
     */
    fun ensureCard(
        branchId: String,
        request: AddInventoryCardRequest,
    ) {
        handler.launchUnit(
            state = _cardResult,
            operation = "ensureCard",
            endpoint = "POST /api/branches/$branchId/inventory",
            block = {
                apiClient.httpClient.post(ApiRoutes.branchInventory(branchId)) {
                    setBody(request)
                }
            },
        )
    }

    fun restock(
        branchId: String,
        productId: String,
        request: RestockRequest,
    ) {
        handler.launch(
            state = _restockResult,
            operation = "restock",
            endpoint = "POST /api/branches/$branchId/inventory/$productId/restock",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.branchInventoryRestock(branchId, productId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun recordMovement(
        branchId: String,
        productId: String,
        request: InventoryMovementRequest,
    ) {
        handler.launch(
            state = _movementResult,
            operation = "recordMovement",
            endpoint = "POST /api/branches/$branchId/inventory/$productId/movement",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.branchInventoryMovement(branchId, productId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    /**
     * #392 — the screen's terminal hook for write results: success legs clear so a repeat
     * action re-fires the StateFlow; the banner's Dismiss clears a standing error.
     * #395 — the ensure-card result rides the same lifecycle.
     */
    fun clearWriteResults() {
        _restockResult.value = UiState.Idle
        _movementResult.value = UiState.Idle
        _cardResult.value = UiState.Idle
    }
}
