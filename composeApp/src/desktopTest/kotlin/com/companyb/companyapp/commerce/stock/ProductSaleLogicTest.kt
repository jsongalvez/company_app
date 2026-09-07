package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// #419 — the product-sale entry flows' pure decision surface, pinned exactly like the backend's
// `POST /api/product-sales` contract: the branch-or-day EDIT_BRANCH_DATA gate, the endpoint's
// 400s (quantity ≥ 1, insufficient stock) mirrored in validation, and the three legal link
// shapes on the outgoing request.
class ProductSaleLogicTest {
    private fun card(
        version: Int = 4,
        stock: Int = 10,
    ) = BranchInventoryResponse(
        id = "card-1",
        branchId = "branch-1",
        productId = "product-1",
        productName = "Alcohol",
        currentStock = stock,
        version = version,
        unitPrice = "150.50",
        commissionAmount = "0",
    )

    private fun cap(
        code: String,
        contextType: CapabilityContextType,
        contextId: String,
    ) = UserCapabilityResponse(code, contextType, contextId, CapabilitySourceType.ROLE)

    private val branchId = "branch-1"
    private val dayId = "day-1"

    @Test
    fun gate_mirrorsTheBranchOrDayRouteFilter_globalNeverPasses() {
        // No capabilities, or no branch/day → no affordance.
        assertFalse(canSellProducts(emptyList(), branchId, dayId))
        assertFalse(canSellProducts(emptyList(), null, null))

        // EDIT_BRANCH_DATA at the branch → sell (the BRANCH leg of the OR).
        assertTrue(
            canSellProducts(listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, branchId)), branchId, dayId),
        )

        // A day grant for this day → sell without any BRANCH grant (the relief leg).
        assertTrue(
            canSellProducts(listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, dayId)), branchId, dayId),
        )

        // Exact-scope: another branch / another day never matches (#156).
        assertFalse(
            canSellProducts(listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, "branch-9")), branchId, dayId),
        )
        assertFalse(
            canSellProducts(
                listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "day-9")),
                branchId,
                dayId,
            ),
        )

        // GLOBAL-scoped EDIT_BRANCH_DATA never satisfies the day-scoped gates (#131).
        assertFalse(
            canSellProducts(listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.GLOBAL, "global")), branchId, dayId),
        )

        // A held BRANCH grant alone satisfies the capability OR; the clocked-in-day
        // requirement is the CALL SITES' fail-closed guard (the request needs the body
        // branchDayId), not part of this matcher.
        assertTrue(
            canSellProducts(listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, branchId)), branchId, null),
        )
    }

    @Test
    fun quantityValidation_mirrorsTheEndpoint400s_plusTheStockGuard() {
        assertEquals("Enter a positive number of units", saleQuantityError("", 10))
        assertEquals("Enter a positive number of units", saleQuantityError("abc", 10))
        assertEquals("Enter a positive number of units", saleQuantityError("0", 10))
        assertEquals("Enter a positive number of units", saleQuantityError("-2", 10))

        // The "Insufficient stock" 400, caught client-side from the card's displayed stock.
        assertEquals("Insufficient stock", saleQuantityError("11", 10))
        assertNull(saleQuantityError(" 3 ", 10))

        // Unknown stock (no card read) skips the cap — the backend stays authoritative.
        assertNull(saleQuantityError("999", null))
    }

    @Test
    fun builder_coversAllThreeLegalLinkShapes_andNormalizesOptionals() {
        // Walk-in, anonymous: isWalkIn=true, no client, no session.
        val anonymous =
            buildSaleRequest(
                SaleDraft(
                    card(version = 7),
                    2,
                    clientId = null,
                    sessionId = null,
                    isWalkIn = true,
                    reason = " ",
                    branchDayId = dayId,
                ),
            )
        assertTrue(anonymous.isWalkIn)
        assertNull(anonymous.clientId)
        assertNull(anonymous.sessionId)
        assertEquals(7, anonymous.expectedVersion)
        assertNull(anonymous.reason) // blank collapses to null (#403 precedent)

        // Walk-in, linked client: isWalkIn=true with the buyer's id.
        val linked =
            buildSaleRequest(
                SaleDraft(
                    card(),
                    1,
                    clientId = "client-9",
                    sessionId = null,
                    isWalkIn = true,
                    reason = " walk-in ",
                    branchDayId = dayId,
                ),
            )
        assertTrue(linked.isWalkIn)
        assertEquals("client-9", linked.clientId)
        assertEquals("walk-in", linked.reason)

        // Session-linked: the session's id, no client, NOT a walk-in.
        val sessionLinked =
            buildSaleRequest(
                SaleDraft(
                    card(),
                    3,
                    clientId = null,
                    sessionId = "session-5",
                    isWalkIn = false,
                    reason = null,
                    branchDayId = dayId,
                ),
            )
        assertFalse(sessionLinked.isWalkIn)
        assertEquals("session-5", sessionLinked.sessionId)
        assertNull(sessionLinked.clientId)

        // Wire fields ride along unchanged; ids are fresh per request (idempotency keys).
        assertEquals("product-1", anonymous.productId)
        assertEquals(2, anonymous.quantity)
        assertEquals(dayId, anonymous.branchDayId)
        assertNotEquals(anonymous.id, buildSaleRequest(SaleDraft(card(), 2, null, null, true, null, dayId)).id)
    }
}
