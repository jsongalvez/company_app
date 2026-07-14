package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        viewModelScope.launch {
            _inventory.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/branches/$branchId/inventory")
                if (response.status.isSuccess()) {
                    _inventory.value = UiState.Success(response.body())
                } else {
                    _inventory.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _inventory.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun ensureCard(
        branchId: String,
        request: AddInventoryCardRequest,
    ) {
        viewModelScope.launch {
            _cardResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/branches/$branchId/inventory") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _cardResult.value = UiState.Success(response.body())
                } else {
                    _cardResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _cardResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun restock(
        branchId: String,
        productId: String,
        request: RestockRequest,
    ) {
        viewModelScope.launch {
            _restockResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/branches/$branchId/inventory/$productId/restock",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _restockResult.value = UiState.Success(response.body())
                } else {
                    _restockResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _restockResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun recordMovement(
        branchId: String,
        productId: String,
        request: InventoryMovementRequest,
    ) {
        viewModelScope.launch {
            _movementResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/branches/$branchId/inventory/$productId/movement",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _movementResult.value = UiState.Success(response.body())
                } else {
                    _movementResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _movementResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
