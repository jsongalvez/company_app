package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProductSaleViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ProductSaleVM")

    private val _saleResult = MutableStateFlow<UiState<ProductSaleResponse>>(UiState.Idle)
    val saleResult: StateFlow<UiState<ProductSaleResponse>> = _saleResult.asStateFlow()

    /** #419 — single-flight: a second submit while the first is in flight is dropped. */
    fun sell(request: CreateProductSaleRequest) {
        if (_saleResult.value is UiState.Loading) return
        handler.launch(
            state = _saleResult,
            operation = "sell",
            endpoint = "POST /api/product-sales",
            block = {
                apiClient.httpClient.post(ApiRoutes.PRODUCT_SALES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    /**
     * #419 — the screens' terminal hook (the InventoryViewModel.clearWriteResults shape):
     * success clears so a repeat sale re-fires the StateFlow; Dismiss clears a standing error.
     */
    fun clearSaleResult() {
        _saleResult.value = UiState.Idle
    }
}
