@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.companyb.companyapp.commerce.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.commerce.CreateProductCategoryRequest
import com.companyb.companyapp.contracts.commerce.CreateProductRequest
import com.companyb.companyapp.contracts.commerce.ProductCategoryResponse
import com.companyb.companyapp.contracts.commerce.ProductResponse
import com.companyb.companyapp.contracts.commerce.UpdateProductRequest
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.DestructiveConfirmDialog
import com.companyb.companyapp.ui.contract.OperationalDialog
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.contract.operationalTouchTarget
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.Uuid

/**
 * #441 — shared product/category catalog administration for GLOBAL MANAGE_CATALOG holders
 * (the #436 scope; route gate mirrors it exactly, backend authoritative). Reuses the
 * previously orphaned [ProductViewModel] category/product legs: category loading/creation,
 * product loading/creation/editing including active-state toggling.
 *
 * #683 — product-first catalog: products own the main surface (header, toolbar, list);
 * taxonomy lives behind Manage categories and inside the product form's nested create.
 * Behavior: entry loads categories + products (inclusive read, so deactivated rows stay
 * available with their Inactive badge and reactivate affordance — the toolbar's default
 * Active filter shows actives, All/Inactive reveal the rest, including rows deactivated
 * by a concurrent admin); the toolbar starts on Active with All-categories and blank
 * search. Each terminal mutation reloads its own collection authoritatively
 * (pessimistic, ADR-0022 — split effects so one mutation's retained terminal never refires
 * another's reload). Server truth is the single source. Failures surface inline with retry
 * (loads via [ErrorCard], mutations by re-submitting the same idempotency id); in-flight
 * mutations disable their affordances (repeated-submission guard, with the ViewModel
 * synchronous single-flight backstop). A just-toggled row stays pinned with completion
 * feedback when its new state leaves the status filter, then reconciles on filter change.
 */
@Suppress("LongMethod") // #683 screen orchestrator stays whole per #535
@Composable
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
    // #683 — fresh idempotency id per create intent: mint on open so a reopened dialog
    // never resubmits the previous product's id (which the backend would 200-overwrite).
    var createRequestId by rememberSaveable { mutableStateOf("") }
    var editingProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(CatalogStatusFilter.ACTIVE) }
    var managingCategories by rememberSaveable { mutableStateOf(false) }
    var confirmDeactivate by remember { mutableStateOf<ProductResponse?>(null) }
    // #683 — pinned-row rule: the just-mutated id + its completion copy. Cleared once the
    // row rejoins the filtered list (reconciled) or the filters change to include it.
    var pinnedMutatedId by remember { mutableStateOf<String?>(null) }
    var pinnedNotice by remember { mutableStateOf<String?>(null) }
    var refocusRowId by remember { mutableStateOf<String?>(null) }
    // #683 — the load state observed when the focus intent armed; the settle effect below
    // only trusts outcomes that replace this instance (never the stale pre-reload state).
    var focusBarrier by remember { mutableStateOf<UiState<List<ProductResponse>>?>(null) }
    // Keep-last lists (the UserViewModel freshest-shape): a reload's Loading/Error never blanks
    // an open dialog's dropdown or closes it by emptying the edited row's lookup.
    var lastProducts by remember { mutableStateOf(emptyList<ProductResponse>()) }
    var lastCategories by remember { mutableStateOf(emptyList<ProductCategoryResponse>()) }

    CatalogScreenEffects(
        viewModel = viewModel,
        productsState = productsState,
        categoriesState = categoriesState,
        createProductState = createProductState,
        updateProductState = updateProductState,
        createCategoryState = createCategoryState,
        onProducts = { lastProducts = it },
        onCategories = { lastCategories = it },
        onProductCreated = { product ->
            creatingProduct = false
            focusBarrier = productsState
            // #683 — the created row pins like a toggle: under a non-matching status
            // filter it stays visible with its completion copy instead of vanishing.
            pinnedMutatedId = product.id
            pinnedNotice = "Created ${product.name}."
            refocusRowId = product.id
        },
        onProductUpdated = { product, notice ->
            editingProductId = null
            confirmDeactivate = null
            focusBarrier = productsState
            pinnedMutatedId = product.id
            pinnedNotice = notice
            refocusRowId = product.id
        },
    )

    val displayedProducts =
        remember(lastProducts, selectedCategoryId, searchQuery, statusFilter) {
            filterCatalogVisibleProducts(lastProducts, selectedCategoryId, searchQuery, statusFilter)
        }
    val visibleProducts = ensureMutatedRowVisible(lastProducts, displayedProducts, pinnedMutatedId)
    // #683 — the edited row resolves against the full keep-last list so a filter change
    // mid-edit never dismisses the dialog by emptying its lookup.
    val editingProduct = lastProducts.firstOrNull { it.id == editingProductId }
    val showPinnedNotice =
        pinnedNotice != null && visibleProducts.size > displayedProducts.size

    // #683 — the pin reconciles once its row rejoins the filtered list.
    LaunchedEffect(displayedProducts) {
        val pinned = pinnedMutatedId
        if (pinned != null && displayedProducts.any { it.id == pinned }) {
            pinnedMutatedId = null
            pinnedNotice = null
        }
    }
    // #683 — a focus intent settles on the first load outcome past its barrier: a mounted
    // row owns focus through the row effect; a missing id or a failed reload drops the
    // intent so it can never fire on an unrelated later transition. The pin survives
    // errors (visibility needs no fresh mount); only the focus jump is dropped.
    LaunchedEffect(productsState, refocusRowId) {
        val target = refocusRowId
        val barrier = focusBarrier
        if (target != null && barrier != null && productsState !== barrier &&
            productsState !is UiState.Idle && productsState !is UiState.Loading
        ) {
            if (productsState is UiState.Success && lastProducts.any { it.id == target }) {
                focusBarrier = null
            } else {
                refocusRowId = null
                focusBarrier = null
            }
        }
    }

    val newTriggerFocus = remember { FocusRequester() }
    val manageTriggerFocus = remember { FocusRequester() }
    var createWasOpen by remember { mutableStateOf(false) }
    var manageWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(creatingProduct) {
        if (creatingProduct) {
            createWasOpen = true
        } else if (createWasOpen) {
            createWasOpen = false
            // #683 — success closes compete with the new row's own refocus: the row wins
            // (nearest surviving control); the trigger only reclaims focus on dismiss.
            if (refocusRowId == null) newTriggerFocus.requestFocus()
        }
    }
    LaunchedEffect(managingCategories) {
        if (managingCategories) {
            manageWasOpen = true
        } else if (manageWasOpen) {
            manageWasOpen = false
            manageTriggerFocus.requestFocus()
        }
    }

    val clearFilters: () -> Unit = {
        searchQuery = ""
        selectedCategoryId = null
        statusFilter = CatalogStatusFilter.ALL
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CatalogHeader(
            categoryCount = lastCategories.size,
            categoriesLoaded = categoriesState is UiState.Success,
            newTriggerFocus = newTriggerFocus,
            manageTriggerFocus = manageTriggerFocus,
            onNewProduct = {
                viewModel.resetCatalogMutationErrors()
                createRequestId = Uuid.random().toString()
                creatingProduct = true
            },
            onManageCategories = {
                viewModel.resetCatalogMutationErrors()
                managingCategories = true
            },
        )
        CatalogToolbar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            categories = lastCategories,
            selectedCategoryId = selectedCategoryId,
            onFilterChange = { selectedCategoryId = it },
            status = statusFilter,
            onStatusChange = { statusFilter = it },
            resultCount = visibleProducts.size,
        )
        CatalogProductSection(
            productsState = productsState,
            categoriesState = categoriesState,
            categories = lastCategories,
            // #683 — the truly-empty branch keys on the unfiltered keep-last list, never on
            // the default Active filter (which would mislabel a fresh catalog as no-match).
            totalProductCount = lastProducts.size,
            displayedProducts = visibleProducts,
            pinnedNotice = pinnedNotice.takeIf { showPinnedNotice },
            updateSaving = updateProductState is UiState.Loading,
            updateError = (updateProductState as? UiState.Error)?.message,
            refocusRowId = refocusRowId,
            onRefocusConsumed = { refocusRowId = null },
            onRetry = { viewModel.loadProducts(includeInactive = true) },
            onRetryCategories = viewModel::loadCategories,
            onClearFilters = clearFilters,
            onCreateClick = {
                viewModel.resetCatalogMutationErrors()
                createRequestId = Uuid.random().toString()
                creatingProduct = true
            },
            onEdit = { product ->
                viewModel.resetCatalogMutationErrors()
                editingProductId = product.id
            },
            onToggleActive = { product ->
                if (product.isActive) {
                    confirmDeactivate = product
                } else {
                    viewModel.updateProduct(product.id, UpdateProductRequest(isActive = true))
                }
            },
        )
    }

    CatalogScreenDialogs(
        viewModel = viewModel,
        creatingProduct = creatingProduct,
        createRequestId = createRequestId,
        editingProduct = editingProduct,
        lastCategories = lastCategories,
        categoriesState = categoriesState,
        createProductState = createProductState,
        updateProductState = updateProductState,
        createCategoryState = createCategoryState,
        managingCategories = managingCategories,
        confirmDeactivate = confirmDeactivate,
        updateSaving = updateProductState is UiState.Loading,
        onDismissCreate = { if (createProductState !is UiState.Loading) creatingProduct = false },
        onDismissEdit = { if (updateProductState !is UiState.Loading) editingProductId = null },
        onDismissManage = { managingCategories = false },
        onDismissDeactivate = { confirmDeactivate = null },
        onConfirmDeactivate = { product ->
            viewModel.updateProduct(product.id, UpdateProductRequest(isActive = false))
        },
    )
}

@Composable
// #683 12-param effects stay whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList", "LongMethod") // #683
private fun CatalogScreenEffects(
    viewModel: ProductViewModel,
    productsState: UiState<List<ProductResponse>>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    createProductState: UiState<ProductResponse>,
    updateProductState: UiState<ProductResponse>,
    createCategoryState: UiState<ProductCategoryResponse>,
    onProducts: (List<ProductResponse>) -> Unit,
    onCategories: (List<ProductCategoryResponse>) -> Unit,
    onProductCreated: (ProductResponse) -> Unit,
    onProductUpdated: (ProductResponse, String) -> Unit,
) {
    LaunchedEffect(Unit) {
        logInfo("ProductCatalogScreen", "composable entered (first composition)")
        viewModel.loadCategories()
        viewModel.loadProducts(includeInactive = true)
    }
    LaunchedEffect(productsState) {
        when (val state = productsState) {
            is UiState.Success -> onProducts(state.data)
            is UiState.Error -> logWarn("ProductCatalogScreen", "loadProducts failed: ${state.message}")
            else -> Unit
        }
    }
    LaunchedEffect(categoriesState) {
        when (val state = categoriesState) {
            is UiState.Success -> onCategories(state.data)
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
                onProductCreated(state.data)
                viewModel.loadProducts(includeInactive = true)
            }

            is UiState.Error -> {
                logWarn("ProductCatalogScreen", "createProduct failed: ${state.message}")
                viewModel.loadProducts(includeInactive = true)
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
                val notice =
                    if (!state.data.isActive) {
                        "${state.data.name} deactivated — it stops being offered for new sales."
                    } else {
                        "${state.data.name} saved."
                    }
                onProductUpdated(state.data, notice)
                viewModel.loadProducts(includeInactive = true)
            }

            is UiState.Error -> {
                logWarn("ProductCatalogScreen", "updateProduct failed: ${state.message}")
                viewModel.loadProducts(includeInactive = true)
            }

            else -> {
                Unit
            }
        }
    }
}

@Composable
// #683 16-param dialogs stay whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #683
private fun CatalogScreenDialogs(
    viewModel: ProductViewModel,
    creatingProduct: Boolean,
    createRequestId: String,
    editingProduct: ProductResponse?,
    lastCategories: List<ProductCategoryResponse>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    createProductState: UiState<ProductResponse>,
    updateProductState: UiState<ProductResponse>,
    createCategoryState: UiState<ProductCategoryResponse>,
    managingCategories: Boolean,
    confirmDeactivate: ProductResponse?,
    updateSaving: Boolean,
    onDismissCreate: () -> Unit,
    onDismissEdit: () -> Unit,
    onDismissManage: () -> Unit,
    onDismissDeactivate: () -> Unit,
    onConfirmDeactivate: (ProductResponse) -> Unit,
) {
    if (creatingProduct) {
        ProductFormDialog(
            product = null,
            createRequestId = createRequestId,
            categories = lastCategories,
            categoriesState = categoriesState,
            saving = createProductState is UiState.Loading,
            serverError = (createProductState as? UiState.Error)?.message,
            createCategoryState = createCategoryState,
            onRetryCategories = viewModel::loadCategories,
            onCreateCategory = { id, name ->
                viewModel.createCategory(CreateProductCategoryRequest(id = id, name = name))
            },
            onDismiss = onDismissCreate,
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
            createRequestId = product.id,
            categories = lastCategories,
            categoriesState = categoriesState,
            saving = updateProductState is UiState.Loading,
            serverError = (updateProductState as? UiState.Error)?.message,
            createCategoryState = createCategoryState,
            onRetryCategories = viewModel::loadCategories,
            onCreateCategory = { id, name ->
                viewModel.createCategory(CreateProductCategoryRequest(id = id, name = name))
            },
            onDismiss = onDismissEdit,
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
    if (managingCategories) {
        ManageCategoriesDialog(
            categories = lastCategories,
            categoriesState = categoriesState,
            createState = createCategoryState,
            onRetry = viewModel::loadCategories,
            onCreate = { id, name ->
                viewModel.createCategory(CreateProductCategoryRequest(id = id, name = name))
            },
            onDismiss = onDismissManage,
        )
    }
    confirmDeactivate?.let { product ->
        DestructiveConfirmDialog(
            title = "Deactivate ${product.name}?",
            body =
                "${product.name} will stop being offered for new sales. " +
                    "History remains.",
            confirmLabel = if (updateSaving) "Deactivating…" else "Deactivate",
            isBusy = updateSaving,
            onConfirm = { onConfirmDeactivate(product) },
            onDismiss = { if (!updateSaving) onDismissDeactivate() },
        )
    }
}

@Composable
private fun CatalogHeader(
    categoryCount: Int,
    categoriesLoaded: Boolean,
    newTriggerFocus: FocusRequester,
    manageTriggerFocus: FocusRequester,
    onNewProduct: () -> Unit,
    onManageCategories: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Product Catalog", style = MaterialTheme.typography.titleLarge)
        Text(
            "Shared products for every branch. Deactivating hides a product from new sales.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PrimaryActionButton(
                label = "New product",
                onClick = onNewProduct,
                modifier = Modifier.focusRequester(newTriggerFocus),
            )
            SecondaryActionButton(
                label = "Manage categories",
                onClick = onManageCategories,
                modifier = Modifier.focusRequester(manageTriggerFocus),
            )
        }
        Text(
            // #683 — the count conveys taxonomy context without listing it above products.
            text =
                if (!categoriesLoaded) {
                    "Loading categories…"
                } else if (categoryCount == 0) {
                    "No categories yet"
                } else if (categoryCount == 1) {
                    "1 category"
                } else {
                    "$categoryCount categories"
                },
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
// #683 9-param toolbar stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #683
private fun CatalogToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<ProductCategoryResponse>,
    selectedCategoryId: String?,
    onFilterChange: (String?) -> Unit,
    status: CatalogStatusFilter,
    onStatusChange: (CatalogStatusFilter) -> Unit,
    resultCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search products") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    // #683 — icon-class clear keeps the 48dp target + focus ring (no
                    // material-icons dependency, so the Latin-1 × glyph stays).
                    Text(
                        text = "×",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .operationalTouchTarget()
                                .operationalFocusRing()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Clear search",
                                    onClick = { onQueryChange("") },
                                ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().operationalFocusRing(),
        )
        if (categories.isNotEmpty()) {
            CatalogCategoryFilter(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onFilterChange = onFilterChange,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val chipModifier = Modifier.operationalTouchTarget().operationalFocusRing()
            FilterChip(
                selected = status == CatalogStatusFilter.ACTIVE,
                onClick = { onStatusChange(CatalogStatusFilter.ACTIVE) },
                label = { Text("Active") },
                modifier = chipModifier,
            )
            FilterChip(
                selected = status == CatalogStatusFilter.INACTIVE,
                onClick = { onStatusChange(CatalogStatusFilter.INACTIVE) },
                label = { Text("Inactive") },
                modifier = chipModifier,
            )
            FilterChip(
                selected = status == CatalogStatusFilter.ALL,
                onClick = { onStatusChange(CatalogStatusFilter.ALL) },
                label = { Text("All") },
                modifier = chipModifier,
            )
            Text(
                text =
                    if (resultCount == 1) {
                        "1 product"
                    } else {
                        "$resultCount products"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

@Composable
// #683: 16-param product section stays whole per #535 (declarative-UI signature).
@Suppress("LongParameterList") // #683
private fun CatalogProductSection(
    productsState: UiState<List<ProductResponse>>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    categories: List<ProductCategoryResponse>,
    totalProductCount: Int,
    displayedProducts: List<ProductResponse>,
    pinnedNotice: String?,
    updateSaving: Boolean,
    updateError: String?,
    refocusRowId: String?,
    onRefocusConsumed: () -> Unit,
    onRetry: () -> Unit,
    onRetryCategories: () -> Unit,
    onClearFilters: () -> Unit,
    onCreateClick: () -> Unit,
    onEdit: (ProductResponse) -> Unit,
    onToggleActive: (ProductResponse) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Products", style = MaterialTheme.typography.titleMedium)
        // #683 — taxonomy failures never blank good products: the rows below keep their
        // keep-last list with "Category unavailable" labels; this band owns the retry.
        if (categoriesState is UiState.Error && displayedProducts.isNotEmpty()) {
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
                TertiaryActionButton(label = "Retry", onClick = onRetryCategories)
            }
        }
        pinnedNotice?.let { notice ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.weight(1f),
                )
                TertiaryActionButton(label = "Clear filters", onClick = onClearFilters)
            }
        }
        updateError?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        when (productsState) {
            is UiState.Idle, is UiState.Loading -> {
                if (displayedProducts.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    CatalogProductList(
                        displayedProducts = displayedProducts,
                        categories = categories,
                        updateSaving = updateSaving,
                        refocusRowId = refocusRowId,
                        onRefocusConsumed = onRefocusConsumed,
                        onEdit = onEdit,
                        onToggleActive = onToggleActive,
                    )
                }
            }

            is UiState.Error -> {
                if (displayedProducts.isEmpty()) {
                    ErrorCard(message = productsState.message, onRetry = onRetry)
                } else {
                    CatalogProductList(
                        displayedProducts = displayedProducts,
                        categories = categories,
                        updateSaving = updateSaving,
                        refocusRowId = refocusRowId,
                        onRefocusConsumed = onRefocusConsumed,
                        onEdit = onEdit,
                        onToggleActive = onToggleActive,
                    )
                }
            }

            is UiState.Success -> {
                if (displayedProducts.isEmpty()) {
                    if (totalProductCount == 0) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        ) {
                            EmptyState("No products yet — create the first one")
                            SecondaryActionButton(label = "New product", onClick = onCreateClick)
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        ) {
                            Text(
                                "No products match these filters",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Try a different search, or clear them to see everything.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSubtle,
                            )
                            SecondaryActionButton(label = "Clear filters", onClick = onClearFilters)
                        }
                    }
                } else {
                    CatalogProductList(
                        displayedProducts = displayedProducts,
                        categories = categories,
                        updateSaving = updateSaving,
                        refocusRowId = refocusRowId,
                        onRefocusConsumed = onRefocusConsumed,
                        onEdit = onEdit,
                        onToggleActive = onToggleActive,
                    )
                }
            }
        }
    }
}

@Composable
// #683 7-param list stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #683
private fun CatalogProductList(
    displayedProducts: List<ProductResponse>,
    categories: List<ProductCategoryResponse>,
    updateSaving: Boolean,
    refocusRowId: String?,
    onRefocusConsumed: () -> Unit,
    onEdit: (ProductResponse) -> Unit,
    onToggleActive: (ProductResponse) -> Unit,
) {
    displayedProducts.forEachIndexed { index, product ->
        CatalogProductRow(
            product = product,
            categoryName = catalogCategoryName(product.productCategoryId, categories),
            chargeLine = catalogCustomerChargeLine(product),
            stateText = catalogStateText(product.isActive),
            saving = updateSaving,
            requestRefocus = refocusRowId == product.id,
            onRefocusConsumed = onRefocusConsumed,
            onEdit = { onEdit(product) },
            onToggleActive = { onToggleActive(product) },
        )
        if (index < displayedProducts.lastIndex) HorizontalDivider()
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
// #683 9-param row stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #683
private fun CatalogProductRow(
    product: ProductResponse,
    categoryName: String,
    chargeLine: String,
    stateText: String,
    saving: Boolean,
    requestRefocus: Boolean,
    onRefocusConsumed: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    // #683 — closing the edit dialog returns focus to this row's Edit (the nearest
    // surviving control when a toggle reconciles the row out of the status filter).
    val editFocus = remember { FocusRequester() }
    LaunchedEffect(requestRefocus) {
        if (requestRefocus) {
            editFocus.requestFocus()
            onRefocusConsumed()
        }
    }
    var moreOpen by remember { mutableStateOf(false) }
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
                modifier =
                    Modifier.weight(1f).operationalFocusRing().clickable(
                        role = Role.Button,
                        onClickLabel = "Edit ${product.name}",
                        onClick = onEdit,
                    ),
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
            TertiaryActionButton(
                label = "Edit",
                onClick = onEdit,
                enabled = !saving,
                modifier = Modifier.focusRequester(editFocus),
            )
            Box {
                TertiaryActionButton(
                    label = "More",
                    onClick = { moreOpen = true },
                    enabled = !saving,
                )
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (product.isActive) "Deactivate" else "Reactivate",
                                color =
                                    if (product.isActive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                        enabled = !saving,
                        onClick = {
                            moreOpen = false
                            onToggleActive()
                        },
                    )
                }
            }
        }
        Text(
            "$categoryName · $chargeLine · $stateText",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
// #683 6-param manage dialog stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #683
private fun ManageCategoriesDialog(
    categories: List<ProductCategoryResponse>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    createState: UiState<ProductCategoryResponse>,
    onRetry: () -> Unit,
    onCreate: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
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

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Categories") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                when (categoriesState) {
                    is UiState.Idle, is UiState.Loading -> {
                        if (categories.isEmpty()) {
                            CircularProgressIndicator()
                        } else {
                            ManageCategoryList(categories = categories)
                        }
                    }

                    is UiState.Error -> {
                        if (categories.isEmpty()) {
                            ErrorCard(message = categoriesState.message, onRetry = onRetry)
                        } else {
                            ManageCategoryList(categories = categories)
                            Text(
                                "Categories unavailable: ${categoriesState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TertiaryActionButton(label = "Retry", onClick = onRetry)
                        }
                    }

                    is UiState.Success -> {
                        if (categories.isEmpty()) {
                            EmptyState("No categories yet — create the first one below")
                        } else {
                            ManageCategoryList(categories = categories)
                        }
                    }
                }
                HorizontalDivider()
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
                    modifier = Modifier.fillMaxWidth(),
                )
                (createState as? UiState.Error)?.let { error ->
                    Text(
                        error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (catalogCategoryNameError(name) == null) onCreate(requestId, name.trim())
                },
                enabled = !saving && catalogCategoryNameError(name) == null,
            ) {
                Text(if (saving) "Adding…" else "Add category")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
        },
    )
}

@Composable
private fun ManageCategoryList(categories: List<ProductCategoryResponse>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        categories.sortedBy { it.name.lowercase() }.forEach { category ->
            Text(category.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// #683: 11-param dialog stays whole per #535 (declarative-UI signature); the product +
// nested-category field/validation contracts split only by ownership, never by count.
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList") // #683
private fun ProductFormDialog(
    product: ProductResponse?,
    createRequestId: String,
    categories: List<ProductCategoryResponse>,
    categoriesState: UiState<List<ProductCategoryResponse>>,
    saving: Boolean,
    serverError: String?,
    createCategoryState: UiState<ProductCategoryResponse>,
    onRetryCategories: () -> Unit,
    onCreateCategory: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, categoryId: String, unitPrice: String, commission: String) -> Unit,
) {
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var categoryId by rememberSaveable(product?.id) { mutableStateOf(product?.productCategoryId) }
    var unitPrice by rememberSaveable(product?.id) { mutableStateOf(product?.unitPrice.orEmpty()) }
    var commission by rememberSaveable(product?.id) { mutableStateOf(product?.commissionAmount.orEmpty()) }
    var attempted by rememberSaveable(product?.id) { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    // #683 — nested create-category child form: one level only, inside this dialog's
    // context. The product draft above is never cleared while it is open; Cancel
    // returns to the untouched draft.
    var creatingNestedCategory by rememberSaveable(product?.id) { mutableStateOf(false) }
    var nestedName by rememberSaveable(product?.id) { mutableStateOf("") }
    var nestedAttempted by rememberSaveable(product?.id) { mutableStateOf(false) }
    var nestedRequestId by rememberSaveable(product?.id) { mutableStateOf(Uuid.random().toString()) }
    var pendingCategoryName by rememberSaveable(product?.id) { mutableStateOf<String?>(null) }
    // #683 — New product focuses Name (the #670 first-field contract via contentFocus).
    val nameFocus = remember { FocusRequester() }
    val nestedSaving = createCategoryState is UiState.Loading

    // #683 — the nested save waits for POST success, then selects the created category
    // and restores the product draft/focus (the draft was never cleared; focus returns
    // to Name). Input is retained on error for retry with the same idempotency id.
    LaunchedEffect(createCategoryState) {
        val state = createCategoryState
        if (creatingNestedCategory && state is UiState.Success) {
            categoryId = state.data.id
            pendingCategoryName = state.data.name
            attempted = true
            creatingNestedCategory = false
            nestedName = ""
            nestedAttempted = false
            nestedRequestId = Uuid.random().toString()
            nameFocus.requestFocus()
        }
    }
    // The authoritative reload resolves the pending name; a later failure keeps the id
    // selected with the pending label rather than reverting the user's choice.
    LaunchedEffect(categories, categoryId) {
        if (categoryId != null && categories.any { it.id == categoryId }) {
            pendingCategoryName = null
        }
    }

    val nameError = catalogProductNameError(name).takeIf { attempted }
    val categoryError = (if (categoryId == null) "Choose a category" else null).takeIf { attempted }
    val priceError = catalogMoneyError(unitPrice, "price").takeIf { attempted || unitPrice.isNotBlank() }
    val commissionError =
        catalogMoneyError(commission, "commission").takeIf { attempted || commission.isNotBlank() }
    val nestedNameError = catalogCategoryNameError(nestedName).takeIf { nestedAttempted }
    val valid =
        catalogProductNameError(name) == null &&
            categoryId != null &&
            catalogMoneyError(unitPrice, "price") == null &&
            catalogMoneyError(commission, "commission") == null
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val totalLine =
        catalogProductTotalLine(unitPrice, commission)
            ?.takeIf { priceError == null && commissionError == null }

    OperationalDialog(
        title = if (product == null) "New product" else "Edit product",
        onDismiss = onDismiss,
        confirmLabel = if (saving) "Saving…" else "Save",
        onConfirm = {
            attempted = true
            val targetCategory = categoryId
            if (valid && targetCategory != null) {
                onSave(createRequestId, name.trim(), targetCategory, unitPrice.trim(), commission.trim())
            }
        },
        isBusy = saving || nestedSaving,
        // #683 — an open nested child owns the dialog's intent: product Save waits for its
        // Save/Cancel so the typed category name is never silently abandoned.
        confirmEnabled = valid && !nestedSaving && !creatingNestedCategory,
        contentFocus = nameFocus,
        content = {
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
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { if (!saving) categoryExpanded = !categoryExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: pendingCategoryName ?: "Choose a category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    isError = categoryError != null,
                    supportingText = categoryError?.let { message -> { Text(message) } },
                    enabled = !saving,
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Create category") },
                        onClick = {
                            nestedAttempted = false
                            creatingNestedCategory = true
                            categoryExpanded = false
                        },
                    )
                    categories.sortedBy { it.name.lowercase() }.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                attempted = true
                                pendingCategoryName = null
                                categoryId = category.id
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }
            if (creatingNestedCategory) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = nestedName,
                        onValueChange = {
                            nestedAttempted = true
                            nestedName = it
                        },
                        label = { Text("New category name") },
                        singleLine = true,
                        enabled = !nestedSaving,
                        isError = nestedNameError != null,
                        supportingText = nestedNameError?.let { message -> { Text(message) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    (createCategoryState as? UiState.Error)?.let { error ->
                        Text(
                            error.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TertiaryActionButton(
                            label = if (nestedSaving) "Saving…" else "Save category",
                            onClick = {
                                nestedAttempted = true
                                if (catalogCategoryNameError(nestedName) == null) {
                                    onCreateCategory(nestedRequestId, nestedName.trim())
                                }
                            },
                            enabled = !nestedSaving && catalogCategoryNameError(nestedName) == null,
                        )
                        TertiaryActionButton(
                            label = "Cancel",
                            onClick = {
                                // #683 — Cancel returns to the untouched product draft.
                                creatingNestedCategory = false
                                nestedName = ""
                                nestedAttempted = false
                            },
                            enabled = !nestedSaving,
                        )
                    }
                }
            } else if (categories.isEmpty()) {
                // #683 — no dead-end message: with zero categories the inline path above
                // is the Create action; this copy only names the state behind it.
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
                        TertiaryActionButton(label = "Retry", onClick = onRetryCategories, enabled = !saving)
                    }
                    TertiaryActionButton(
                        label = "Create category",
                        onClick = { creatingNestedCategory = true },
                        enabled = !saving,
                    )
                } else {
                    TertiaryActionButton(
                        label = "Create the first category",
                        onClick = { creatingNestedCategory = true },
                        enabled = !saving,
                    )
                }
            }
            OutlinedTextField(
                value = unitPrice,
                onValueChange = {
                    attempted = true
                    unitPrice = it
                },
                label = { Text("Base price (₱)") },
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
            totalLine?.let { total ->
                Text(total, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }
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
        },
    )
}

private const val INACTIVE_ROW_ALPHA = 0.6f
