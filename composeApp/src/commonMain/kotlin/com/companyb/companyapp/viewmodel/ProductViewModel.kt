package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProductViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ProductVM")

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
        handler.launch(
            state = _products,
            operation = "loadProducts",
            endpoint = "GET /api/products",
            block = { apiClient.httpClient.get(ApiRoutes.PRODUCTS) },
            transform = { it.body() },
        )
    }

    fun loadProduct(productId: String) {
        handler.launch(
            state = _productDetail,
            operation = "loadProduct",
            endpoint = "GET /api/products/$productId",
            block = { apiClient.httpClient.get(ApiRoutes.product(productId)) },
            transform = { it.body() },
        )
    }

    fun createProduct(request: CreateProductRequest) {
        handler.launch(
            state = _createProductResult,
            operation = "createProduct",
            endpoint = "POST /api/products",
            block = {
                apiClient.httpClient.post(ApiRoutes.PRODUCTS) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun updateProduct(
        productId: String,
        request: UpdateProductRequest,
    ) {
        handler.launch(
            state = _updateProductResult,
            operation = "updateProduct",
            endpoint = "PATCH /api/products/$productId",
            block = {
                apiClient.httpClient.patch(ApiRoutes.product(productId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun loadCategories() {
        handler.launch(
            state = _categories,
            operation = "loadCategories",
            endpoint = "GET /api/product-categories",
            block = { apiClient.httpClient.get(ApiRoutes.PRODUCT_CATEGORIES) },
            transform = { it.body() },
        )
    }

    fun createCategory(request: CreateProductCategoryRequest) {
        handler.launch(
            state = _createCategoryResult,
            operation = "createCategory",
            endpoint = "POST /api/product-categories",
            block = {
                apiClient.httpClient.post(ApiRoutes.PRODUCT_CATEGORIES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
