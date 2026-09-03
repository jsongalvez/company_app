@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.dto.CreateProductCategoryRequest
import com.companyb.companyapp.dto.CreateProductRequest
import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UpdateProductRequest
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ProductViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.Uuid

/**
 * #441 — shared product/category catalog administration for GLOBAL MANAGE_CATALOG holders
 * (the #436 scope; route gate mirrors it exactly, backend authoritative). Reuses the
 * previously orphaned [ProductViewModel] category/product legs: category loading/creation,
 * product loading/creation/editing including active-state toggling.
 *
 * Behavior: entry loads categories + products; each terminal mutation reloads its own
 * collection authoritatively (pessimistic, ADR-0022 — split effects so one mutation's
 * retained terminal never refires another's reload). The collection read is active-only, so
 * this session's mutation landings gap-fill it ([mergeCatalogProducts] — deactivated rows
 * stay visible with their Inactive badge); server-returned rows always win. Failures surface
 * inline with retry (loads via [ErrorCard], mutations by re-submitting the same idempotency
 * id); in-flight mutations disable their affordances (repeated-submission guard, with the
 * ViewModel synchronous single-flight backstop).
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ProductCatalogScreen(
    viewModel: ProductViewModel,
    modifier: Modifier = Modifier,
) {
    val productsState by viewModel.products.collectAsState()
    val categoriesState by viewModel.categories.collectAsState()
    val createProductState by viewModel.createProductResult.collectAsState()
    val updateProductState by viewModel.updateProductResult.collectAsState()
    val createCategoryState by viewModel.createCategoryResult.collectAsState()

    var creatingProduct by rememberSaveable { mutableStateOf(false) }
    var editingProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    // Keep-last lists (the UserViewModel freshest-shape): a reload's Loading/Error never blanks
    // an open dialog's dropdown or closes it by emptying the edited row's lookup.
    var lastProducts by remember { mutableStateOf(emptyList<ProductResponse>()) }
    var lastCategories by remember { mutableStateOf(emptyList<ProductCategoryResponse>()) }
    val overlays = remember { mutableStateMapOf<String, ProductResponse>() }

    LaunchedEffect(Unit) {
        logInfo("ProductCatalogScreen", "composable entered (first composition)")
        viewModel.loadCategories()
        viewModel.loadProducts()
    }
    LaunchedEffect(productsState) {
        when (val state = productsState) {
            is UiState.Success -> lastProducts = state.data
            is UiState.Error -> logWarn("ProductCatalogScreen", "loadProducts failed: ${state.message}")
            else -> Unit
        }
    }
    LaunchedEffect(categoriesState) {
        when (val state = categoriesState) {
            is UiState.Success -> lastCategories = state.data
            is UiState.Error -> logWarn("ProductCatalogScreen", "loadCategories failed: ${state.message}")
            else -> Unit
        }
    }
    LaunchedEffect(createCategoryState) {
        when (val state = createCategoryState) {
            is UiState.Success -> {
                logInfo("ProductCatalogScreen", "createCategory succeeded")
                viewModel.loadCategories()
            }

            is UiState.Error -> {
                logWarn("ProductCatalogScreen", "createCategory failed: ${state.message}")
                viewModel.loadCategories()
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(createProductState) {
        when (val state = createProductState) {
            is UiState.Success -> {
                logInfo("ProductCatalogScreen", "createProduct succeeded")
                overlays[state.data.id] = state.data
                creatingProduct = false
                viewModel.loadProducts()
            }

            is UiState.Error -> {
                logWarn("ProductCatalogScreen", "createProduct failed: ${state.message}")
                viewModel.loadProducts()
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(updateProductState) {
        when (val state = updateProductState) {
            is UiState.Success -> {
                logInfo("ProductCatalogScreen", "updateProduct succeeded")
                overlays[state.data.id] = state.data
                editingProductId = null
                viewModel.loadProducts()
            }

            is UiState.Error -> {
                logWarn("ProductCatalogScreen", "updateProduct failed: ${state.message}")
                viewModel.loadProducts()
            }

            else -> {
                Unit
            }
        }
    }

    val displayedProducts =
        remember(lastProducts, overlays.toMap(), selectedCategoryId) {
            filterCatalogProducts(mergeCatalogProducts(lastProducts, overlays), selectedCategoryId)
        }
    val editingProduct = displayedProducts.firstOrNull { it.id == editingProductId }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Product Catalog", style = MaterialTheme.typography.titleLarge)
        Text(
            "Shared categories and products. Deactivating hides a product from every branch.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        CatalogCategorySection(
            categoriesState = categoriesState,
            createState = createCategoryState,
            onRetry = viewModel::loadCategories,
            onCreate = { id, name ->
                viewModel.createCategory(CreateProductCategoryRequest(id = id, name = name))
            },
        )
        HorizontalDivider()
        CatalogProductSection(
            productsState = productsState,
            categories = lastCategories,
            displayedProducts = displayedProducts,
            selectedCategoryId = selectedCategoryId,
            updateSaving = updateProductState is UiState.Loading,
            updateError = (updateProductState as? UiState.Error)?.message,
            onFilterChange = { selectedCategoryId = it },
            onRetry = viewModel::loadProducts,
            onCreateClick = {
                viewModel.resetCatalogMutationErrors()
                creatingProduct = true
            },
            onEdit = { product ->
                viewModel.resetCatalogMutationErrors()
                editingProductId = product.id
            },
            onToggleActive = { product ->
                viewModel.updateProduct(product.id, UpdateProductRequest(isActive = !product.isActive))
            },
        )
    }

    if (creatingProduct) {
        ProductFormDialog(
            product = null,
            categories = lastCategories,
            categoriesState = categoriesState,
            saving = createProductState is UiState.Loading,
            serverError = (createProductState as? UiState.Error)?.message,
            onRetryCategories = viewModel::loadCategories,
            onDismiss = { if (createProductState !is UiState.Loading) creatingProduct = false },
            onSave = { id, name, categoryId, unitPrice, commission ->
                viewModel.createProduct(
                    CreateProductRequest(
                        id = id,
                        name = name,
                        productCategoryId = categoryId,
                        unitPrice = unitPrice,
                        commissionAmount = commission,
                    ),
                )
            },
        )
    }
    editingProduct?.let { product ->
        ProductFormDialog(
            product = product,
            categories = lastCategories,
            categoriesState = categoriesState,
            saving = updateProductState is UiState.Loading,
            serverError = (updateProductState as? UiState.Error)?.message,
            onRetryCategories = viewModel::loadCategories,
            onDismiss = { if (updateProductState !is UiState.Loading) editingProductId = null },
            // Field edits omit isActive (null = no change): the toggle owns the flag, so a
            // stale dialog snapshot can never overwrite a concurrent toggle.
            onSave = { _, name, categoryId, unitPrice, commission ->
                viewModel.updateProduct(
                    product.id,
                    UpdateProductRequest(
                        name = name,
                        productCategoryId = categoryId,
                        unitPrice = unitPrice,
                        commissionAmount = commission,
                    ),
                )
            },
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun CatalogCategorySection(
    categoriesState: UiState<List<ProductCategoryResponse>>,
    createState: UiState<ProductCategoryResponse>,
    onRetry: () -> Unit,
    onCreate: (id: String, name: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    // Stable idempotency id per form intent: retries resubmit the same id (the backend
    // answers 200 for the existing row) instead of minting duplicates. Reset on success.
    var requestId by rememberSaveable { mutableStateOf(Uuid.random().toString()) }
    val saving = createState is UiState.Loading
    val nameError = catalogCategoryNameError(name).takeIf { attempted }

    LaunchedEffect(createState) {
        if (createState is UiState.Success) {
            name = ""
            attempted = false
            requestId = Uuid.random().toString()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Categories", style = MaterialTheme.typography.titleMedium)
        when (val state = categoriesState) {
            is UiState.Idle, is UiState.Loading -> {
                CircularProgressIndicator()
            }

            is UiState.Error -> {
                ErrorCard(message = state.message, onRetry = onRetry)
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState("No categories yet — create the first one below")
                } else {
                    state.data.sortedBy { it.name.lowercase() }.forEach { category ->
                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    attempted = true
                    name = it
                },
                label = { Text("New category name") },
                singleLine = true,
                enabled = !saving,
                isError = nameError != null,
                supportingText = nameError?.let { message -> { Text(message) } },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    attempted = true
                    if (catalogCategoryNameError(name) == null) onCreate(requestId, name.trim())
                },
                enabled = !saving,
            ) {
                Text(if (saving) "Adding…" else "Add")
            }
        }
        (createState as? UiState.Error)?.let { error ->
            Text(error.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun CatalogProductSection(
    productsState: UiState<List<ProductResponse>>,
    categories: List<ProductCategoryResponse>,
    displayedProducts: List<ProductResponse>,
    selectedCategoryId: String?,
    updateSaving: Boolean,
    updateError: String?,
    onFilterChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onCreateClick: () -> Unit,
    onEdit: (ProductResponse) -> Unit,
    onToggleActive: (ProductResponse) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Products", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onCreateClick) { Text("New product") }
        }
        if (categories.isNotEmpty()) {
            CatalogCategoryFilter(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onFilterChange = onFilterChange,
            )
        }
        updateError?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        when (productsState) {
            is UiState.Idle, is UiState.Loading -> {
                CircularProgressIndicator()
            }

            is UiState.Error -> {
                ErrorCard(message = productsState.message, onRetry = onRetry)
            }

            is UiState.Success -> {
                if (displayedProducts.isEmpty()) {
                    EmptyState(
                        if (selectedCategoryId == null) {
                            "No products yet — create the first one above"
                        } else {
                            "No products in this category"
                        },
                    )
                } else {
                    displayedProducts.forEachIndexed { index, product ->
                        CatalogProductRow(
                            product = product,
                            categoryName = catalogCategoryName(product.productCategoryId, categories),
                            saving = updateSaving,
                            onEdit = { onEdit(product) },
                            onToggleActive = { onToggleActive(product) },
                        )
                        if (index < displayedProducts.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogCategoryFilter(
    categories: List<ProductCategoryResponse>,
    selectedCategoryId: String?,
    onFilterChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.name ?: "All categories",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category filter") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All categories") },
                onClick = {
                    onFilterChange(null)
                    expanded = false
                },
            )
            categories.sortedBy { it.name.lowercase() }.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onFilterChange(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogProductRow(
    product: ProductResponse,
    categoryName: String,
    saving: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().alpha(if (product.isActive) 1f else INACTIVE_ROW_ALPHA),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge)
                if (!product.isActive) {
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.sm),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.xs),
                        )
                    }
                }
            }
            TextButton(onClick = onEdit, enabled = !saving) { Text("Edit") }
        }
        Text(
            "$categoryName · ₱${product.unitPrice} + ₱${product.commissionAmount} commission",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        TextButton(onClick = onToggleActive, enabled = !saving) {
            Text(if (product.isActive) "Deactivate" else "Reactivate")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
private fun ProductFormDialog(
    product: ProductResponse?,
    categories: List<ProductCategoryResponse>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    saving: Boolean,
    serverError: String?,
    onRetryCategories: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, categoryId: String, unitPrice: String, commission: String) -> Unit,
) {
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var categoryId by rememberSaveable(product?.id) { mutableStateOf(product?.productCategoryId) }
    var unitPrice by rememberSaveable(product?.id) { mutableStateOf(product?.unitPrice.orEmpty()) }
    var commission by rememberSaveable(product?.id) { mutableStateOf(product?.commissionAmount.orEmpty()) }
    var attempted by rememberSaveable(product?.id) { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    // Stable idempotency id per dialog intent (same shape as the category form above).
    var requestId by rememberSaveable(product?.id) { mutableStateOf(Uuid.random().toString()) }

    val nameError = catalogProductNameError(name).takeIf { attempted }
    val categoryError = (if (categoryId == null) "Choose a category" else null).takeIf { attempted }
    val priceError = catalogMoneyError(unitPrice, "price").takeIf { attempted || unitPrice.isNotBlank() }
    val commissionError =
        catalogMoneyError(commission, "commission").takeIf { attempted || commission.isNotBlank() }
    val valid =
        catalogProductNameError(name) == null &&
            categoryId != null &&
            catalogMoneyError(unitPrice, "price") == null &&
            catalogMoneyError(commission, "commission") == null
    val selectedCategory = categories.firstOrNull { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "New product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        attempted = true
                        name = it
                    },
                    label = { Text("Product name") },
                    singleLine = true,
                    enabled = !saving,
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { if (!saving) categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Choose a category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        isError = categoryError != null,
                        supportingText = categoryError?.let { message -> { Text(message) } },
                        enabled = !saving && categories.isNotEmpty(),
                        modifier =
                            Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        categories.sortedBy { it.name.lowercase() }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    attempted = true
                                    categoryId = category.id
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
                if (categories.isEmpty()) {
                    if (categoriesState is UiState.Error) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Categories unavailable: ${categoriesState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetryCategories, enabled = !saving) { Text("Retry") }
                        }
                    } else {
                        Text(
                            "Create a category first — products need exactly one",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                        )
                    }
                }
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = {
                        attempted = true
                        unitPrice = it
                    },
                    label = { Text("Unit price (₱)") },
                    singleLine = true,
                    enabled = !saving,
                    isError = priceError != null,
                    supportingText = priceError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commission,
                    onValueChange = {
                        attempted = true
                        commission = it
                    },
                    label = { Text("Commission (₱)") },
                    singleLine = true,
                    enabled = !saving,
                    isError = commissionError != null,
                    supportingText = commissionError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (product != null && !product.isActive) {
                    Text(
                        "This product is inactive — edit fields or reactivate it from the list",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
                serverError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    val targetCategory = categoryId
                    if (valid && targetCategory != null) {
                        onSave(requestId, name.trim(), targetCategory, unitPrice.trim(), commission.trim())
                    }
                },
                enabled = !saving && valid,
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

private const val INACTIVE_ROW_ALPHA = 0.6f
