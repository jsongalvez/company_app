package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse

/**
 * #441 — the catalog admin screen's pure decision surface, pinned in commonTest like the
 * #392 inventory predicates and the #418 rate-input mirror. Mirrors the backend's exact
 * validation (`ProductRoutes`/`ProductCategoryRoutes` 400s via `parseNonNegativeBigDecimal`):
 * blank names and unparseable/negative money are rejected inline; the backend stays
 * authoritative.
 */
internal fun catalogCategoryNameError(raw: String): String? = if (raw.isBlank()) "Enter a category name" else null

internal fun catalogProductNameError(raw: String): String? = if (raw.isBlank()) "Enter a product name" else null

internal fun catalogMoneyError(
    raw: String,
    label: String,
): String? {
    if (raw.isBlank()) return "Enter $label"
    val value = raw.trim().toDoubleOrNull() ?: return "Invalid $label amount: $raw"
    if (!value.isFinite()) return "Invalid $label amount: $raw"
    return if (value < 0) "$label must be non-negative" else null
}

/** Display name for a product's category; unknown ids fail closed to a placeholder. */
internal fun catalogCategoryName(
    productCategoryId: String,
    categories: List<ProductCategoryResponse>,
): String = categories.firstOrNull { it.id == productCategoryId }?.name ?: "Unknown category"

/**
 * Gap-fills the active-only collection read with this session's mutation landings.
 * `GET /api/products` returns active products only, so a freshly deactivated row would vanish
 * from the list with no path back (no all-products endpoint — out of scope for #441); rows the
 * server still returns always win (freshest truth, including concurrent edits), and overlays
 * only fill ids the collection omits (deactivated rows, a created row pre-reload).
 */
internal fun mergeCatalogProducts(
    loaded: List<ProductResponse>,
    overlays: Map<String, ProductResponse>,
): List<ProductResponse> {
    val merged = loaded.associateBy { it.id }.toMutableMap()
    overlays.forEach { (id, row) ->
        if (id !in merged) merged[id] = row
    }
    return merged.values.sortedBy { it.name.lowercase() }
}

/** Category filter for the product list; null means all categories. */
internal fun filterCatalogProducts(
    products: List<ProductResponse>,
    categoryId: String?,
): List<ProductResponse> =
    if (categoryId == null) {
        products
    } else {
        products.filter { it.productCategoryId == categoryId }
    }
