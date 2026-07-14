package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _products = MutableStateFlow<UiState<List<ProductResponse>>>(UiState.Idle)
    val products: StateFlow<UiState<List<ProductResponse>>> = _products.asStateFlow()

    private val _productDetail = MutableStateFlow<UiState<ProductResponse>>(UiState.Idle)
    val productDetail: StateFlow<UiState<ProductResponse>> = _productDetail.asStateFlow()

    private val _createProductResult = MutableStateFlow<UiState<ProductResponse>>(UiState.Idle)
    val createProductResult: StateFlow<UiState<ProductResponse>> = _createProductResult.asStateFlow()

    private val _updateProductResult = MutableStateFlow<UiState<ProductResponse>>(UiState.Idle)
    val updateProductResult: StateFlow<UiState<ProductResponse>> = _updateProductResult.asStateFlow()

    private val _categories = MutableStateFlow<UiState<List<ProductCategoryResponse>>>(UiState.Idle)
    val categories: StateFlow<UiState<List<ProductCategoryResponse>>> = _categories.asStateFlow()

    private val _createCategoryResult = MutableStateFlow<UiState<ProductCategoryResponse>>(UiState.Idle)
    val createCategoryResult: StateFlow<UiState<ProductCategoryResponse>> = _createCategoryResult.asStateFlow()

    fun loadProducts() {
        viewModelScope.launch {
            _products.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/products")
                if (response.status.isSuccess()) {
                    _products.value = UiState.Success(response.body())
                } else {
                    _products.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _products.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            _productDetail.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/products/$productId")
                if (response.status.isSuccess()) {
                    _productDetail.value = UiState.Success(response.body())
                } else {
                    _productDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _productDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createProduct(request: CreateProductRequest) {
        viewModelScope.launch {
            _createProductResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/products") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createProductResult.value = UiState.Success(response.body())
                } else {
                    _createProductResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createProductResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateProduct(
        productId: String,
        request: UpdateProductRequest,
    ) {
        viewModelScope.launch {
            _updateProductResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch("/api/products/$productId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _updateProductResult.value = UiState.Success(response.body())
                } else {
                    _updateProductResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _updateProductResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _categories.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/product-categories")
                if (response.status.isSuccess()) {
                    _categories.value = UiState.Success(response.body())
                } else {
                    _categories.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _categories.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createCategory(request: CreateProductCategoryRequest) {
        viewModelScope.launch {
            _createCategoryResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/product-categories") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createCategoryResult.value = UiState.Success(response.body())
                } else {
                    _createCategoryResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createCategoryResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
