package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.network.ApiClient
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
        viewModelScope.launch {
            _saleResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/product-sales") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _saleResult.value = UiState.Success(response.body())
                } else {
                    _saleResult.value = UiState.Error("Sale failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _saleResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
