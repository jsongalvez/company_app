package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductSaleViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _saleResult = MutableStateFlow<UiState<ProductSaleResponse>>(UiState.Idle)
    val saleResult: StateFlow<UiState<ProductSaleResponse>> = _saleResult.asStateFlow()

    fun sell(request: CreateProductSaleRequest) {
        logInfo("ProductSaleVM", "sell called")
        viewModelScope.launch {
            _saleResult.value = UiState.Loading
            logInfo("ProductSaleVM", "POST /api/product-sales")
            try {
                val response =
                    apiClient.httpClient.post("/api/product-sales") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ProductSaleVM", "sell success")
                    _saleResult.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductSaleVM", "sell failed: status=${response.status.value}")
                    _saleResult.value = UiState.Error("Sale failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductSaleVM", "sell exception", e)
                _saleResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
