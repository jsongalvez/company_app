package com.companyb.companyapp.commerce.catalog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.workforce.team.extractApiErrorMessage
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProductViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ProductVM")
    private val handleApiError: suspend (HttpResponse, (UiState.Error) -> Unit) -> Boolean = { response, setError ->
        val detail = extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
        if (detail == null) {
            false
        } else {
            setError(UiState.Error(detail))
            true
        }
    }

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

    fun loadProducts(includeInactive: Boolean = false) {
        val path = ApiRoutes.productsList(includeInactive)
        handler.launch(
            state = _products,
            operation = "loadProducts",
            endpoint = "GET $path",
            block = { apiClient.httpClient.get(path) },
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
        // Single-flight like BranchViewModel.createBranch: the Loading preset is synchronous so
        // a double-tap before recomposition cannot mint two product ids (the second POST would
        // 200-overwrite the first's row).
        if (_createProductResult.value is UiState.Loading) return
        _createProductResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = _createProductResult,
                operation = "createProduct",
                endpoint = "POST /api/products",
                block = {
                    apiClient.httpClient.post(ApiRoutes.PRODUCTS) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
                onNonSuccess = { response ->
                    handleApiError(response) { _createProductResult.value = it }
                },
            ),
        )
    }

    fun updateProduct(
        productId: String,
        request: UpdateProductRequest,
    ) {
        if (_updateProductResult.value is UiState.Loading) return
        _updateProductResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = _updateProductResult,
                operation = "updateProduct",
                endpoint = "PATCH /api/products/$productId",
                block = {
                    apiClient.httpClient.patch(ApiRoutes.product(productId)) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
                onNonSuccess = { response ->
                    handleApiError(response) { _updateProductResult.value = it }
                },
            ),
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
        if (_createCategoryResult.value is UiState.Loading) return
        _createCategoryResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = _createCategoryResult,
                operation = "createCategory",
                endpoint = "POST /api/product-categories",
                block = {
                    apiClient.httpClient.post(ApiRoutes.PRODUCT_CATEGORIES) {
                        setBody(request)
                    }
                },
                transform = { it.body() },
                onNonSuccess = { response ->
                    handleApiError(response) { _createCategoryResult.value = it }
                },
            ),
        )
    }

    /**
     * Clears terminal mutation ERRORS so a reopened dialog starts clean. Success rows are kept:
     * the inclusive collection read already returns deactivated rows, so no overlay is needed.
     */
    fun resetCatalogMutationErrors() {
        if (_createProductResult.value is UiState.Error) {
            _createProductResult.value = UiState.Idle
        }
        if (_updateProductResult.value is UiState.Error) {
            _updateProductResult.value = UiState.Idle
        }
        if (_createCategoryResult.value is UiState.Error) {
            _createCategoryResult.value = UiState.Idle
        }
    }
}
