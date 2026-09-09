package com.companyb.companyapp.commerce.catalog

import com.companyb.companyapp.contracts.commerce.ProductCategoryResponse
import com.companyb.companyapp.contracts.commerce.ProductResponse

/**
 * #441 — the catalog admin screen's pure decision surface, pinned in commonTest like the
 * #392 inventory predicates and the #418 rate-input mirror. Mirrors the backend's exact
 * validation (`ProductRoutes`/`ProductCategoryRoutes` 400s via `parseNonNegativeBigDecimal`):
 * blank names and unparseable/negative money are rejected inline; the backend stays
 * authoritative.
 *
 * #683 — product-first catalog: search + status filtering, customer-charge formatting,
 * and the pinned-row rule that keeps a just-toggled product visible through its
 * completion feedback even when the new state no longer matches the status filter.
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

/** Display name for a product's category; unknown ids fail closed to a fallback label. */
internal fun catalogCategoryName(
    productCategoryId: String,
    categories: List<ProductCategoryResponse>,
): String = categories.firstOrNull { it.id == productCategoryId }?.name ?: "Category unavailable"

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

/** #683 — status filter for the product list; the toolbar starts on Active. */
internal enum class CatalogStatusFilter {
    ACTIVE,
    INACTIVE,
    ALL,
}

/** #683 — name search; blank matches everything (case-insensitive contains). */
internal fun catalogMatchesQuery(
    product: ProductResponse,
    query: String,
): Boolean {
    if (query.isBlank()) return true
    return product.name.contains(query.trim(), ignoreCase = true)
}

/** #683 — status predicate behind [filterCatalogVisibleProducts]; ALL matches everything. */
internal fun catalogMatchesStatus(
    product: ProductResponse,
    status: CatalogStatusFilter,
): Boolean =
    when (status) {
        CatalogStatusFilter.ACTIVE -> product.isActive
        CatalogStatusFilter.INACTIVE -> !product.isActive
        CatalogStatusFilter.ALL -> true
    }

/**
 * #683 — the visible product list: category, then search, then status. Pure so the
 * no-match vs truly-empty empty states stay decidable without a Compose runtime.
 */
internal fun filterCatalogVisibleProducts(
    products: List<ProductResponse>,
    categoryId: String?,
    query: String,
    status: CatalogStatusFilter,
): List<ProductResponse> =
    products.filter { product ->
        (categoryId == null || product.productCategoryId == categoryId) &&
            catalogMatchesQuery(product, query) &&
            catalogMatchesStatus(product, status)
    }

/** #683 — list-row state text; inactive rows also carry reduced emphasis + badge. */
internal fun catalogStateText(isActive: Boolean): String = if (isActive) "Active" else "Inactive"

/**
 * #683 — list-row customer unit charge. Unparseable amounts read "Unavailable", never a
 * fabricated zero (the #672/#676 pattern).
 */
internal fun catalogCustomerChargeLine(product: ProductResponse): String {
    val unit = runCatching { product.unitPrice.trim().toDouble() }.getOrNull()
    if (unit == null || !unit.isFinite() || unit < 0) return "Charge unavailable"
    return "₱${product.unitPrice.trim()}"
}

/**
 * #683 — detail total from the existing base + commission inputs (the sale insert's
 * `unitPrice * quantity` shape keeps unitPrice as the customer charge; the dialog
 * surfaces base and commission separately with their sum for review).
 */
internal fun catalogProductTotalLine(
    unitPrice: String,
    commission: String,
): String? {
    val unit = unitPrice.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val fee = commission.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    if (unit < 0 || fee < 0) return null
    return "Total ₱${unit + fee} (base ₱${unitPrice.trim()} + ₱${commission.trim()} commission)"
}

/**
 * #683 — a just-toggled product stays mounted even when its new state no longer
 * matches the status filter, so its completion feedback has a visible anchor (the
 * #672 edited-row pattern). Cleared on the next filter change.
 */
internal fun ensureMutatedRowVisible(
    all: List<ProductResponse>,
    visible: List<ProductResponse>,
    mutatedId: String?,
): List<ProductResponse> {
    if (mutatedId == null) return visible
    if (visible.any { it.id == mutatedId }) return visible
    val pinned = all.firstOrNull { it.id == mutatedId } ?: return visible
    return visible + pinned
}
