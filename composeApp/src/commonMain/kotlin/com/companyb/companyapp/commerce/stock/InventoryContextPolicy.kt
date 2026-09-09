package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse

/**
 * #727 — identity anchor resolution for Inventory section roundtrips.
 *
 * The retained list position is the visible card id (not a raw index), resolved against
 * the currently filtered cards on return. A missing anchor (removed card, or one no
 * longer matching the restored filter) falls back to the list start — never a blank or
 * impossible scroll state. Null anchor (cold first visit) also starts at 0.
 */
internal fun inventoryAnchorIndex(
    cards: List<BranchInventoryResponse>,
    anchorId: String?,
): Int {
    if (anchorId == null) return 0
    val index = cards.indexOfFirst { it.id == anchorId }
    return if (index >= 0) index else 0
}
