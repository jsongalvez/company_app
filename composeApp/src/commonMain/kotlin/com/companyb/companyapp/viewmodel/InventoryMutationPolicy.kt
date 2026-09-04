// #460 — one policy seam, intentionally co-located: 20 functions over the 11
// file budget, kept whole deliberately (#458 locality — predicates, builders, and arms
// must read as one contract mirror). Any further growth must split, not suppress again.
@file:Suppress("TooManyFunctions")

package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.CreateProductSaleRequest
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.state.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.state.hasBranchOrDayCapability
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.util.logInfo
import java.util.UUID

/**
 * #458 — the one inventory-mutation policy seam: every inventory write (restock, movement,
 * ensure-card, walk-in sale) shares its capability predicates and its effect/refresh wiring
 * here, so screens cannot disagree on who may mutate and what refreshes. The pure decision
 * surface moved verbatim from `ui.screen.InventoryWriteLogic` + `ui.screen.ProductSaleLogic`
 * (#392/#395/#419); dialogs keep only field state + validation display and submit through
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

/** Everything [buildRestockRequest] needs; ids are generated fresh inside the builder. */
internal data class RestockDraft(
    val card: BranchInventoryResponse,
    val units: Int,
    val editReason: String?,
    val branchDayId: String,
)

/** Caller supplies validated, parsed units (positive); sent as-is per the restock contract. */
internal fun buildRestockRequest(draft: RestockDraft): RestockRequest =
    RestockRequest(
        id = UUID.randomUUID().toString(),
        quantity = draft.units,
        branchDayId = draft.branchDayId,
        editReason = normalizeOptional(draft.editReason),
    )

/** Everything [buildMovementRequest] needs; ids are generated fresh inside the builder. */
internal data class MovementDraft(
    val card: BranchInventoryResponse,
    val reason: InventoryMovementReason,
    val units: Int,
    val notes: String?,
    val editReason: String?,
    val branchDayId: String,
)

/**
 * Tester/Sample/Missing arrive as negative quantityChanges (the user enters units moved out);
 * Adjustment keeps its sign. [InventoryMovementRequest.expectedVersion] rides along unchanged
 * — the backend is lock-only today and ignores it (out-of-scope note on #392).
 */
internal fun buildMovementRequest(draft: MovementDraft): InventoryMovementRequest =
    InventoryMovementRequest(
        movementId = UUID.randomUUID().toString(),
        reason = draft.reason,
        quantityChange =
            if (draft.reason in NEGATIVE_MOVEMENT_REASONS) -draft.units else draft.units,
        notes = normalizeOptional(draft.notes),
        branchDayId = draft.branchDayId,
        expectedVersion = draft.card.version,
        editReason = normalizeOptional(draft.editReason),
    )

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

/** The restock arm's submit: save closes immediately at the call site (the ProfileScreen
 * precedent); ids are fresh client-generated UUIDs and branchDayId is the clocked-in day.
 * A null branch/day fails closed — the affordances already hide, this is the backstop. */
@Suppress("LongParameterList") // #460 — same fail-closed arm shape as the sibling submits below.
internal fun submitRestock(
    viewModel: InventoryViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    units: Int,
    editReason: String?,
) {
    if (branchId != null && branchDayId != null) {
        viewModel.restock(
            branchId = branchId,
            productId = card.productId,
            request = buildRestockRequest(RestockDraft(card, units, editReason, branchDayId)),
        )
    }
}

/** The movement arm's submit: same close-first + fail-closed shape as [submitRestock]. */
@Suppress("LongParameterList")
internal fun submitMovement(
    viewModel: InventoryViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    reason: InventoryMovementReason,
    units: Int,
    notes: String?,
    editReason: String?,
) {
    if (branchId != null && branchDayId != null) {
        viewModel.recordMovement(
            branchId = branchId,
            productId = card.productId,
            request = buildMovementRequest(MovementDraft(card, reason, units, notes, editReason, branchDayId)),
        )
    }
}

/** The walk-in sale arm's submit (#419): same close-first + fail-closed shape as [submitRestock]. */
@Suppress("LongParameterList")
internal fun submitWalkInSale(
    saleViewModel: ProductSaleViewModel,
    branchId: String?,
    branchDayId: String?,
    card: BranchInventoryResponse,
    quantity: Int,
    clientId: String?,
    editReason: String?,
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
                ),
            ),
        )
    }
}
