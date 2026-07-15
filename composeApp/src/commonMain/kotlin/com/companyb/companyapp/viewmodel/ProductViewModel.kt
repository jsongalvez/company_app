package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
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
        logInfo("ProductVM", "loadProducts called")
        viewModelScope.launch {
            _products.value = UiState.Loading
            logInfo("ProductVM", "GET /api/products")
            try {
                val response = apiClient.httpClient.get("/api/products")
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "loadProducts success")
                    _products.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "loadProducts failed: status=${response.status.value}")
                    _products.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "loadProducts exception", e)
                _products.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadProduct(productId: String) {
        logInfo("ProductVM", "loadProduct called")
        viewModelScope.launch {
            _productDetail.value = UiState.Loading
            logInfo("ProductVM", "GET /api/products/$productId")
            try {
                val response = apiClient.httpClient.get("/api/products/$productId")
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "loadProduct success")
                    _productDetail.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "loadProduct failed: status=${response.status.value}")
                    _productDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "loadProduct exception", e)
                _productDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createProduct(request: CreateProductRequest) {
        logInfo("ProductVM", "createProduct called")
        viewModelScope.launch {
            _createProductResult.value = UiState.Loading
            logInfo("ProductVM", "POST /api/products")
            try {
                val response =
                    apiClient.httpClient.post("/api/products") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "createProduct success")
                    _createProductResult.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "createProduct failed: status=${response.status.value}")
                    _createProductResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "createProduct exception", e)
                _createProductResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateProduct(
        productId: String,
        request: UpdateProductRequest,
    ) {
        logInfo("ProductVM", "updateProduct called")
        viewModelScope.launch {
            _updateProductResult.value = UiState.Loading
            logInfo("ProductVM", "PATCH /api/products/$productId")
            try {
                val response =
                    apiClient.httpClient.patch("/api/products/$productId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "updateProduct success")
                    _updateProductResult.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "updateProduct failed: status=${response.status.value}")
                    _updateProductResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "updateProduct exception", e)
                _updateProductResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadCategories() {
        logInfo("ProductVM", "loadCategories called")
        viewModelScope.launch {
            _categories.value = UiState.Loading
            logInfo("ProductVM", "GET /api/product-categories")
            try {
                val response = apiClient.httpClient.get("/api/product-categories")
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "loadCategories success")
                    _categories.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "loadCategories failed: status=${response.status.value}")
                    _categories.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "loadCategories exception", e)
                _categories.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createCategory(request: CreateProductCategoryRequest) {
        logInfo("ProductVM", "createCategory called")
        viewModelScope.launch {
            _createCategoryResult.value = UiState.Loading
            logInfo("ProductVM", "POST /api/product-categories")
            try {
                val response =
                    apiClient.httpClient.post("/api/product-categories") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ProductVM", "createCategory success")
                    _createCategoryResult.value = UiState.Success(response.body())
                } else {
                    logInfo("ProductVM", "createCategory failed: status=${response.status.value}")
                    _createCategoryResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ProductVM", "createCategory exception", e)
                _createCategoryResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
