// #597 — one policy seam, intentionally co-located: 20 functions over the retired 11
// file budget, kept whole deliberately (#458 locality — predicates, builders, and arms
// must read as one contract mirror). Any further growth must split, not suppress again.

package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.CreateProductSaleRequest
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.commerce.InventoryMovementRequest
import com.companyb.companyapp.contracts.commerce.ProductResponse
import com.companyb.companyapp.contracts.commerce.RestockRequest
import com.companyb.companyapp.util.logInfo

/**
 * #458 — the one inventory-mutation policy seam: every inventory write (restock, movement,
 * ensure-card, walk-in sale) shares its capability predicates and its effect/refresh wiring
 * here, so screens cannot disagree on who may mutate and what refreshes. The pure decision
 * surface moved verbatim from the pre-#562 `ui.screen` write/sale logic
 * (#392/#395/#419, now colocated in commerce/stock); dialogs keep only field
 * state + validation display and submit through
 * the [submitRestock]/[submitMovement]/[submitWalkInSale] arms, and the screen's load effects
 * + error banner + row affordances read the [consumeWriteSuccess]/[firstWriteError]/
 * [inventoryRowActions] predicates instead of recomputing them inline.
 *
 * Contract mirror (never widen): restock/ensure-card require branch MANAGE_PRODUCTS, movements
 * split Tester/Sample/Missing (branch EDIT_BRANCH_DATA) vs Adjustment (branch MANAGE_PRODUCTS),
 * sales ride the branch-or-day EDIT_BRANCH_DATA gate — the backend route filters stay
 * authoritative (#157 branch-scoped only, #131 GLOBAL-never).
 */
internal val NEGATIVE_MOVEMENT_REASONS: Set<InventoryMovementReason> =
    setOf(InventoryMovementReason.TESTER, InventoryMovementReason.SAMPLE, InventoryMovementReason.MISSING)

internal fun canRestock(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): Boolean = capabilities.hasCapability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, selectedBranchId)

/**
 * #395 — the ensure-card route filter (`POST /branches/{id}/inventory` → MANAGE_PRODUCTS,
 * branch-scoped exact-scope) mirrored for the Add-card affordance. Happens to equal
 * [canRestock] today; kept separate so the two routes can diverge without a silent drift.
 * Unlike restock/movement there is NO day-state leg: the backend command opens no branch day.
 *
 * #441 — the picker source (`GET /api/products`) additionally requires GLOBAL MANAGE_CATALOG
 * (#436), so the affordance requires both: a branch-MANAGE_PRODUCTS holder without the catalog
 * grant would only land on the picker's 403 (fail closed, no dead-end affordance).
 */
internal fun canEnsureCard(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): Boolean =
    capabilities.hasCapability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, selectedBranchId) &&
        capabilities.hasCapability(
            CapabilityCodes.MANAGE_CATALOG,
            CapabilityContextType.GLOBAL,
            GLOBAL_CAPABILITY_CONTEXT_ID,
        )

/** Catalog products that don't yet have a card at this branch, in picker display order (#395). */
internal fun productsWithoutCards(
    products: List<ProductResponse>,
    cards: List<BranchInventoryResponse>,
): List<ProductResponse> {
    val carded = cards.map { it.productId }.toHashSet()
    return products.filter { it.id !in carded }.sortedBy { it.name.lowercase() }
}

/** The reasons this caller may record at the branch, in picker display order. */
internal fun allowedMovementReasons(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): List<InventoryMovementReason> =
    buildList {
        if (capabilities.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                selectedBranchId,
            )
        ) {
            add(InventoryMovementReason.TESTER)
            add(InventoryMovementReason.SAMPLE)
            add(InventoryMovementReason.MISSING)
        }
        if (capabilities.hasCapability(
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityContextType.BRANCH,
                selectedBranchId,
            )
        ) {
            add(InventoryMovementReason.ADJUSTMENT)
        }
    }

/**
 * #419 — the product-sale gate (`POST /api/product-sales` → `requireBranchOrBranchDayCapability`
 * EDIT_BRANCH_DATA on the body's branch day) mirrored via the shared [hasBranchOrDayCapability]
 * matcher — BRANCH-context at the branch OR a day grant for that day; GLOBAL never satisfies
 * it (#131 strictness). Affordances also hide fail-closed without a clocked-in `branchDayId`
 * (the sale rides that day) — enforced at the call sites, not in this matcher.
 */
internal fun canSellProducts(
    capabilities: List<UserCapabilityResponse>,
    branchId: String?,
    dayId: String?,
): Boolean = capabilities.hasBranchOrDayCapability(CapabilityCodes.EDIT_BRANCH_DATA, branchId, dayId)

/**
 * Units-field validation mirroring the endpoint's 400s: Tester/Sample/Missing demand a
 * positive count (the client negates before sending); Adjustment takes any non-zero signed
 * quantity; the RESTOCK/SALE enum legs are unreachable through this dialog.
 */
internal fun movementUnitsError(
    reason: InventoryMovementReason,
    unitsText: String,
): String? {
    val units = unitsText.trim().toIntOrNull()
    val valid =
        if (reason == InventoryMovementReason.ADJUSTMENT) {
            units != 0
        } else {
            units != null && units > 0
        }
    return if (valid) {
        null
    } else if (reason == InventoryMovementReason.ADJUSTMENT) {
        "Enter a non-zero signed quantity"
    } else {
        "Enter a positive number of units"
    }
}

/** The endpoint's "Notes are required for MISSING movements" 400, mirrored client-side. */
internal fun movementNotesError(
    reason: InventoryMovementReason,
    notes: String?,
): String? =
    if (reason == InventoryMovementReason.MISSING && notes.isNullOrBlank()) {
        "Notes are required for Missing movements"
    } else {
        null
    }

internal fun restockUnitsError(unitsText: String): String? =
    if ((unitsText.trim().toIntOrNull() ?: 0) > 0) null else "Restock quantity must be positive"

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

/** Blank/whitespace-only text collapses to null (shared by the builders and history rows). */
internal fun normalizeOptional(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

/** Everything [buildRestockRequest] needs; ids are stable per dialog instance (#676). */
internal data class RestockDraft(
    val card: BranchInventoryResponse,
    val units: Int,
    val editReason: String?,
    val branchDayId: String,
    val operationId: String,
)

/** Caller supplies validated, parsed units (positive); sent as-is per the restock contract. */
internal fun buildRestockRequest(draft: RestockDraft): RestockRequest =
    RestockRequest(
        id = draft.operationId,
        quantity = draft.units,
        branchDayId = draft.branchDayId,
        editReason = normalizeOptional(draft.editReason),
    )

/** Everything [buildMovementRequest] needs; ids are stable per dialog instance (#676). */
internal data class MovementDraft(
    val card: BranchInventoryResponse,
    val reason: InventoryMovementReason,
    val units: Int,
    val notes: String?,
    val editReason: String?,
    val branchDayId: String,
    val operationId: String,
)

/**
 * Tester/Sample/Missing arrive as negative quantityChanges (the user enters units moved out);
 * Adjustment keeps its sign. [InventoryMovementRequest.expectedVersion] rides along unchanged
 * — the backend is lock-only today and ignores it (out-of-scope note on #392).
 */
internal fun buildMovementRequest(draft: MovementDraft): InventoryMovementRequest =
    InventoryMovementRequest(
        movementId = draft.operationId,
        reason = draft.reason,
        quantityChange =
            if (draft.reason in NEGATIVE_MOVEMENT_REASONS) -draft.units else draft.units,
        notes = normalizeOptional(draft.notes),
        branchDayId = draft.branchDayId,
        expectedVersion = draft.card.version,
        editReason = normalizeOptional(draft.editReason),
    )

/** Everything [buildSaleRequest] needs; ids are stable per dialog instance (#676). */
internal data class SaleDraft(
    val card: BranchInventoryResponse,
    val quantity: Int,
    val clientId: String?,
    val sessionId: String?,
    val isWalkIn: Boolean,
    val reason: String?,
    val branchDayId: String,
    val operationId: String,
)

/**
 * Builds the wire request. The link rules are structural (each dialog offers only legal
 * shapes): session-linked passes no clientId with isWalkIn=false; walk-in sales carry
 * isWalkIn=true with an optional clientId. [normalizeOptional] collapses blank reasons to null
 * so the non-remitted wire stays byte-identical to omitting the field (#403 precedent).
 */
internal fun buildSaleRequest(draft: SaleDraft): CreateProductSaleRequest =
    CreateProductSaleRequest(
        id = draft.operationId,
        branchDayId = draft.branchDayId,
        sessionId = draft.sessionId,
        clientId = draft.clientId,
        isWalkIn = draft.isWalkIn,
        productId = draft.card.productId,
        quantity = draft.quantity,
        expectedVersion = draft.card.version,
        reason = normalizeOptional(draft.reason),
    )

/** Per-row affordance enablement: visibility AND mid-flight disablement merged fail-closed. */
internal data class InventoryRowActions(
    val restockEnabled: Boolean,
    val movementEnabled: Boolean,
    val sellEnabled: Boolean,
)

/**
 * Per-row affordance enablement (#392/#419): the exact branch-or-day gates mirrored fail-closed
 * without a clocked-in branchDayId, and any in-flight write disables the row's actions.
 */
internal fun inventoryRowActions(
    capabilities: List<UserCapabilityResponse>,
    branchId: String?,
    branchDayId: String?,
    writesDisabled: Boolean,
): InventoryRowActions =
    InventoryRowActions(
        restockEnabled = branchDayId != null && canRestock(capabilities, branchId) && !writesDisabled,
        movementEnabled =
            branchDayId != null &&
                allowedMovementReasons(capabilities, branchId).isNotEmpty() &&
                !writesDisabled,
        sellEnabled =
            branchId != null &&
                branchDayId != null &&
                canSellProducts(capabilities, branchId, branchDayId) &&
                !writesDisabled,
    )

/**
 * The single mutation-busy predicate: any in-flight write leg disables every row action, so a
 * stale-row second write can never dispatch behind a landing refresh. The ensure-card leg is
 * deliberately excluded — its header affordance carries its own Loading disablement.
 */
internal fun writesDisabled(
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    saleResult: UiState<*>,
): Boolean =
    restockResult is UiState.Loading ||
        movementResult is UiState.Loading ||
        saleResult is UiState.Loading

/**
 * The screen banner's single error source, in the legs' display order (restock → movement →
 * card → sale). Callers compare the winner against the sale leg to pick the dismiss target.
 */
internal fun firstWriteError(
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    cardResult: UiState<*>,
    saleResult: UiState<*>,
): UiState.Error? =
    (restockResult as? UiState.Error)
        ?: (movementResult as? UiState.Error)
        ?: (cardResult as? UiState.Error)
        ?: (saleResult as? UiState.Error)

/**
 * One write-success leg's terminal wiring (#392 + #395 + #419): a landed success clears the
 * sticky inventory results (so a repeat action re-fires the StateFlow) and reloads both list
 * legs authoritatively. Every overlay arm funnels through this — none refreshes inline.
 */
internal fun consumeWriteSuccess(
    viewModel: InventoryViewModel,
    branchId: String?,
    result: UiState<*>,
    leg: String,
) {
    if (result is UiState.Success<*>) {
        logInfo("InventoryScreen", "$leg landed — refreshing inventory")
        viewModel.clearWriteResults()
        if (branchId != null) viewModel.refresh(branchId)
    }
}

/** The restock arm's submit: retained dialog resubmits the same operationId (#676);
 * a null branch/day fails closed — the affordances already hide, this is the backstop.
 *
 * #597: fail-closed arm shape shared with the sibling submits below; stays whole per #535.
 */
@Suppress("LongParameterList") // #597
internal fun submitRestock(
    viewModel: InventoryViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    units: Int,
    editReason: String?,
    operationId: String,
) {
    if (branchId != null && branchDayId != null) {
        viewModel.restock(
            branchId = branchId,
            productId = card.productId,
            request = buildRestockRequest(RestockDraft(card, units, editReason, branchDayId, operationId)),
        )
    }
}

/** The movement arm's submit: same retained-ID + fail-closed shape as [submitRestock].
 *
 * #597: fail-closed arm shape shared with the sibling submits; stays whole per #535.
 */
@Suppress("LongParameterList") // #597
internal fun submitMovement(
    viewModel: InventoryViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    reason: InventoryMovementReason,
    units: Int,
    notes: String?,
    editReason: String?,
    operationId: String,
) {
    if (branchId != null && branchDayId != null) {
        viewModel.recordMovement(
            branchId = branchId,
            productId = card.productId,
            request =
                buildMovementRequest(
                    MovementDraft(card, reason, units, notes, editReason, branchDayId, operationId),
                ),
        )
    }
}

/** The walk-in sale arm's submit (#419): same retained-ID + fail-closed shape as [submitRestock].
 *
 * #597: fail-closed arm shape shared with the sibling submits; stays whole per #535.
 */
@Suppress("LongParameterList") // #597
internal fun submitWalkInSale(
    saleViewModel: ProductSaleViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    quantity: Int,
    clientId: String?,
    editReason: String?,
    operationId: String,
) {
    if (branchId != null && branchDayId != null) {
        saleViewModel.sell(
            buildSaleRequest(
                SaleDraft(
                    card = card,
                    quantity = quantity,
                    clientId = clientId,
                    sessionId = null,
                    isWalkIn = true,
                    reason = editReason,
                    branchDayId = branchDayId,
                    operationId = operationId,
                ),
            ),
        )
    }
}

/**
 * #676 — list filtering for the header search + Low stock filter. Search matches product
 * name case-insensitively (substring); the low-stock leg only narrows when enabled so the
 * independent endpoint stays authoritative. Pure so the empty states stay testable:
 * search-no-match vs low-stock-empty vs no-cards.
 */
internal fun filterInventoryCards(
    cards: List<BranchInventoryResponse>,
    query: String,
    lowStockOnly: Boolean,
    lowStockIds: Set<String>,
): List<BranchInventoryResponse> {
    val trimmed = query.trim()
    return cards.filter { card ->
        val matchesQuery = trimmed.isEmpty() || card.productName.contains(trimmed, ignoreCase = true)
        val matchesStock = !lowStockOnly || card.productId in lowStockIds
        matchesQuery && matchesStock
    }
}

/** #676 — sale price lines from the card's authoritative unit charge (never a second formula). */
internal data class SalePriceDisplay(
    val unitLine: String,
    val totalLine: String,
    val commissionLine: String?,
)

/**
 * #676 — unit charge is the customer charge (commission included per the ticket); total is
 * unit × quantity (the backend's `unitPrice * quantity` insert). Commission rides secondary.
 * Unparseable amounts read "Unavailable", never a fabricated zero (the #672 pattern).
 */
internal fun salePriceDisplay(
    card: BranchInventoryResponse,
    quantity: Int,
): SalePriceDisplay {
    val unit =
        runCatching { java.math.BigDecimal(card.unitPrice) }.getOrNull()
    val commission =
        runCatching { java.math.BigDecimal(card.commissionAmount) }.getOrNull()
    if (unit == null) {
        return SalePriceDisplay(
            unitLine = "Unit charge unavailable",
            totalLine = "Total unavailable",
            commissionLine = null,
        )
    }
    val total = unit.multiply(java.math.BigDecimal.valueOf(quantity.toLong()))
    return SalePriceDisplay(
        unitLine = "₱${unit.toPlainString()} each",
        totalLine = "Total ₱${total.toPlainString()} for $quantity",
        commissionLine =
            commission?.let { "Includes ₱${it.toPlainString()} commission" }
                ?: "Commission unavailable",
    )
}

/**
 * #676 — retained-draft error guidance. Authoritative rejections (stock/capability/version)
 * keep the draft for review; unknown-client 404 (#684, still open) maps to pick-another-client
 * once that backend fix lands — the message match is forward-compatible, never a duplicate fix.
 */
internal fun writeErrorHint(message: String): String? =
    when {
        message.contains("404", ignoreCase = true) -> {
            "Selected entry is no longer available — choose another and retry (your entries are kept)."
        }

        message.contains("409", ignoreCase = true) -> {
            "Changed elsewhere — review the latest quantities, then retry (your entries are kept)."
        }

        message.contains("403", ignoreCase = true) -> {
            "Not authorized for this change — your entries are kept."
        }

        else -> {
            null
        }
    }

/**
 * #676 — ambiguous outcomes (transport/timeout, no status code to reconcile against) must
 * not invite a fresh operation with a new identifier: state "could not confirm" and keep
 * the same operationId for Retry. Status-bearing failures ("failed: 400/404/…") and
 * authoritative domain messages are authoritative, not ambiguous — only transport-class
 * signals count as ambiguous.
 */
internal fun isAmbiguousWriteError(message: String): Boolean {
    val lower = message.lowercase()
    return listOf(
        "timeout",
        "timed out",
        "network",
        "unreachable",
        "unknown error",
        "unable to resolve",
        "connection",
        "eof",
        "socket",
        "ssl",
        "interrupted",
        "could not confirm",
    ).any { lower.contains(it) }
}

/** #676 — ambiguous-write copy: reconcile before enabling a fresh operation. */
internal const val AMBIGUOUS_WRITE_MESSAGE = "Could not confirm whether this was saved — Retry to reconcile."

/**
 * #676 — version/stock reconcile trigger: a 409 or an insufficient-stock 400 means the
 * dialog's snapshot is stale. The host refreshes the list legs (dialog draft stays
 * retained) so Retry re-sends the same operationId against fresh versions.
 */
internal fun needsVersionReconcile(message: String): Boolean {
    if (message.contains("409")) return true
    return message.lowercase().contains("insufficient stock")
}
