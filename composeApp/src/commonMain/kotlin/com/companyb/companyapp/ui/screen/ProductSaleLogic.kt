package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.state.hasBranchOrDayCapability
import java.util.UUID

/**
 * #419 — the product-sale entry flows' pure decision surface, pinned for desktopTest like the
 * #392 inventory-write logic. Mirrors the backend's exact gates and 400s:
 *
 * - The create gate (`POST /api/product-sales` → `requireBranchOrBranchDayCapability`
 *   EDIT_BRANCH_DATA on the body's branch day) is mirrored by [canSellProducts] via the shared
 *   [hasBranchOrDayCapability] matcher — BRANCH-context at the branch OR a day grant for that
 *   day; GLOBAL never satisfies it (#131 strictness). Affordances also hide fail-closed without
 *   a clocked-in `branchDayId` (the sale rides that day).
 * - Validation mirrors the endpoint's 400s ("Quantity must be at least 1") plus the stock guard
 *   ("Insufficient stock") so an impossible sale never leaves the dialog.
 */
internal fun canSellProducts(
    capabilities: List<UserCapabilityResponse>,
    branchId: String?,
    dayId: String?,
): Boolean = capabilities.hasBranchOrDayCapability(CapabilityCodes.EDIT_BRANCH_DATA, branchId, dayId)

/**
 * Quantity-field validation mirroring the endpoint's 400s: at least 1, and never more than the
 * card's displayed stock when one is known (the "Insufficient stock" 400, caught client-side).
 */
internal fun saleQuantityError(
    quantityText: String,
    availableStock: Int?,
): String? {
    val quantity = quantityText.trim().toIntOrNull() ?: return "Enter a positive number of units"
    if (quantity < 1) return "Enter a positive number of units"
    if (availableStock != null && quantity > availableStock) return "Insufficient stock"
    return null
}

/** Everything [buildSaleRequest] needs; ids are generated fresh inside the builder. */
internal data class SaleDraft(
    val card: BranchInventoryResponse,
    val quantity: Int,
    val clientId: String?,
    val sessionId: String?,
    val isWalkIn: Boolean,
    val reason: String?,
    val branchDayId: String,
)

/**
 * Builds the wire request. The link rules are structural (each dialog offers only legal
 * shapes): session-linked passes no clientId with isWalkIn=false; walk-in sales carry
 * isWalkIn=true with an optional clientId. [normalizeOptional] collapses blank reasons to null
 * so the non-remitted wire stays byte-identical to omitting the field (#403 precedent).
 */
internal fun buildSaleRequest(draft: SaleDraft): CreateProductSaleRequest =
    CreateProductSaleRequest(
        id = UUID.randomUUID().toString(),
        branchDayId = draft.branchDayId,
        sessionId = draft.sessionId,
        clientId = draft.clientId,
        isWalkIn = draft.isWalkIn,
        productId = draft.card.productId,
        quantity = draft.quantity,
        expectedVersion = draft.card.version,
        reason = normalizeOptional(draft.reason),
    )
