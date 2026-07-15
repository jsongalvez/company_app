package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _inventory = MutableStateFlow<UiState<List<BranchInventoryResponse>>>(UiState.Idle)
    val inventory: StateFlow<UiState<List<BranchInventoryResponse>>> = _inventory.asStateFlow()

    private val _cardResult = MutableStateFlow<UiState<BranchInventoryResponse>>(UiState.Idle)
    val cardResult: StateFlow<UiState<BranchInventoryResponse>> = _cardResult.asStateFlow()

    private val _restockResult = MutableStateFlow<UiState<InventoryMovementResponse>>(UiState.Idle)
    val restockResult: StateFlow<UiState<InventoryMovementResponse>> = _restockResult.asStateFlow()

    private val _movementResult = MutableStateFlow<UiState<InventoryMovementResponse>>(UiState.Idle)
    val movementResult: StateFlow<UiState<InventoryMovementResponse>> = _movementResult.asStateFlow()

    fun loadInventory(branchId: String) {
        logInfo("InventoryVM", "loadInventory called")
        viewModelScope.launch {
            _inventory.value = UiState.Loading
            try {
                logInfo("InventoryVM", "GET /api/branches/$branchId/inventory")
                val response = apiClient.httpClient.get("/api/branches/$branchId/inventory")
                if (response.status.isSuccess()) {
                    logInfo("InventoryVM", "loadInventory success")
                    _inventory.value = UiState.Success(response.body())
                } else {
                    logInfo("InventoryVM", "loadInventory failed: status=${response.status.value}")
                    _inventory.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("InventoryVM", "loadInventory exception", e)
                _inventory.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun ensureCard(
        branchId: String,
        request: AddInventoryCardRequest,
    ) {
        logInfo("InventoryVM", "ensureCard called")
        viewModelScope.launch {
            _cardResult.value = UiState.Loading
            try {
                logInfo("InventoryVM", "POST /api/branches/$branchId/inventory")
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/inventory") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("InventoryVM", "ensureCard success")
                    _cardResult.value = UiState.Success(response.body())
                } else {
                    logInfo("InventoryVM", "ensureCard failed: status=${response.status.value}")
                    _cardResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("InventoryVM", "ensureCard exception", e)
                _cardResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun restock(
        branchId: String,
        productId: String,
        request: RestockRequest,
    ) {
        logInfo("InventoryVM", "restock called")
        viewModelScope.launch {
            _restockResult.value = UiState.Loading
            try {
                logInfo("InventoryVM", "POST /api/branches/$branchId/inventory/$productId/restock")
                val response =
                    apiClient.httpClient.post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("InventoryVM", "restock success")
                    _restockResult.value = UiState.Success(response.body())
                } else {
                    logInfo("InventoryVM", "restock failed: status=${response.status.value}")
                    _restockResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("InventoryVM", "restock exception", e)
                _restockResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun recordMovement(
        branchId: String,
        productId: String,
        request: InventoryMovementRequest,
    ) {
        logInfo("InventoryVM", "recordMovement called")
        viewModelScope.launch {
            _movementResult.value = UiState.Loading
            try {
                logInfo("InventoryVM", "POST /api/branches/$branchId/inventory/$productId/movement")
                val response =
                    apiClient.httpClient.post(
                        "/api/branches/$branchId/inventory/$productId/movement",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("InventoryVM", "recordMovement success")
                    _movementResult.value = UiState.Success(response.body())
                } else {
                    logInfo("InventoryVM", "recordMovement failed: status=${response.status.value}")
                    _movementResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("InventoryVM", "recordMovement exception", e)
                _movementResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
