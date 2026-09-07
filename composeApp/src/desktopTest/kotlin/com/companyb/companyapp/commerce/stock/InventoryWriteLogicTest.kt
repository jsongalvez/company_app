package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// #392 — the inventory write flows' pure decision surface, pinned exactly like the backend's
// #157 branch-scoped route filters: gate split by capability, the endpoint's 400s mirrored in
// validation, and the negative-reason negation on the outgoing request.
class InventoryWriteLogicTest {
    private fun card(version: Int = 4) =
        BranchInventoryResponse(
            id = "card-1",
            branchId = "branch-1",
            productId = "product-1",
            productName = "Alcohol",
            currentStock = 10,
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

    @Test
    fun gates_splitByCapability_exactlyLikeTheBackendRouteFilters() {
        // No capabilities → no affordances, even at a held branch.
        assertFalse(canRestock(emptyList(), branchId))
        assertTrue(allowedMovementReasons(emptyList(), branchId).isEmpty())

        // EDIT_BRANCH_DATA at the branch → Tester/Sample/Missing only; no restock.
        val editor =
            listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, branchId))
        assertFalse(canRestock(editor, branchId))
        assertEquals(
            listOf(InventoryMovementReason.TESTER, InventoryMovementReason.SAMPLE, InventoryMovementReason.MISSING),
            allowedMovementReasons(editor, branchId),
        )

        // MANAGE_PRODUCTS at the branch → restock + Adjustment joins the picker.
        val manager =
            listOf(cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH, branchId))
        assertTrue(canRestock(manager, branchId))
        assertEquals(listOf(InventoryMovementReason.ADJUSTMENT), allowedMovementReasons(manager, branchId))

        // Exact-scope: the same codes at ANOTHER branch or a day grant never match (#156).
        val elsewhere =
            listOf(
                cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH, "branch-9"),
                cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "day-1"),
            )
        assertFalse(canRestock(elsewhere, branchId))
        assertTrue(allowedMovementReasons(elsewhere, branchId).isEmpty())
        assertFalse(canRestock(manager, null))
    }

    @Test
    fun movementValidation_mirrorsTheEndpoint400s() {
        // Positive-units rule for Tester/Sample/Missing (the client negates before sending).
        assertEquals("Enter a positive number of units", movementUnitsError(InventoryMovementReason.TESTER, "0"))
        assertEquals("Enter a positive number of units", movementUnitsError(InventoryMovementReason.SAMPLE, "-2"))
        assertNull(movementUnitsError(InventoryMovementReason.MISSING, " 3 "))

        // Adjustment takes any non-zero signed quantity.
        assertEquals("Enter a non-zero signed quantity", movementUnitsError(InventoryMovementReason.ADJUSTMENT, "0"))
        assertNull(movementUnitsError(InventoryMovementReason.ADJUSTMENT, "-5"))
        assertNull(movementUnitsError(InventoryMovementReason.ADJUSTMENT, "+2"))

        // Notes are required for MISSING movements only.
        assertEquals(
            "Notes are required for Missing movements",
            movementNotesError(InventoryMovementReason.MISSING, " "),
        )
        assertEquals(
            "Notes are required for Missing movements",
            movementNotesError(InventoryMovementReason.MISSING, null),
        )
        assertNull(movementNotesError(InventoryMovementReason.MISSING, "counted twice"))
        assertNull(movementNotesError(InventoryMovementReason.TESTER, null))

        // Restock mirrors its own 400 ("Restock quantity must be positive").
        assertEquals("Restock quantity must be positive", restockUnitsError("0"))
        assertNull(restockUnitsError("12"))
    }

    @Test
    fun builders_negateNegativeReasons_carryVersion_andNormalizeOptionals() {
        val request =
            buildMovementRequest(
                MovementDraft(
                    card = card(version = 7),
                    reason = InventoryMovementReason.TESTER,
                    units = 2,
                    notes = "  demo unit  ",
                    editReason = " ",
                    branchDayId = "day-1",
                ),
            )
        assertEquals(-2, request.quantityChange)
        assertEquals(7, request.expectedVersion)
        assertEquals("demo unit", request.notes)
        assertNull(request.editReason)

        // Adjustment keeps its sign; blank notes normalize to null.
        val adjustment =
            buildMovementRequest(MovementDraft(card(), InventoryMovementReason.ADJUSTMENT, 3, "", null, "day-1"))
        assertEquals(3, adjustment.quantityChange)
        assertNull(adjustment.notes)

        val restock = buildRestockRequest(RestockDraft(card(), 12, " box arrived ", "day-1"))
        assertEquals(12, restock.quantity)
        assertEquals("box arrived", restock.editReason)
        assertEquals("day-1", restock.branchDayId)
    }

    private fun product(
        id: String,
        name: String,
    ) = ProductResponse(id, name, "category-1", isActive = true, unitPrice = "100.00", commissionAmount = "0")

    @Test
    fun canEnsureCard_mirrorsThePostInventoryRouteFilter_noDayStateLeg() {
        // No capabilities and no branch → no affordance.
        assertFalse(canEnsureCard(emptyList(), branchId))
        assertFalse(canEnsureCard(emptyList(), null))

        // #441 — the picker source (GET /api/products) needs GLOBAL MANAGE_CATALOG: branch
        // MANAGE_PRODUCTS alone would only land on the picker's 403, so no affordance.
        val manager =
            listOf(cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH, branchId))
        assertFalse(canEnsureCard(manager, branchId))

        // Both legs (branch ensure right + global catalog read) → the affordance shows.
        val catalogManager =
            manager +
                cap(
                    "MANAGE_CATALOG",
                    CapabilityContextType.GLOBAL,
                    com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID,
                )
        assertTrue(canEnsureCard(catalogManager, branchId))

        // Exact-scope: another branch or a day grant never matches; a held branch with a null
        // selection hides it (#156). NOTE — unlike restock/movement there is deliberately NO
        // clocked-in-day requirement here: the backend command opens no branch day.
        val elsewhere =
            listOf(
                cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH, "branch-9"),
                cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH_DAY, "day-1"),
            )
        assertFalse(canEnsureCard(elsewhere, branchId))
    }

    @Test
    fun productsWithoutCards_filtersCardedProducts_sortsCaseInsensitive() {
        val products =
            listOf(
                product("p-3", "beta"),
                product("p-1", "Alcohol"),
                product("p-2", "alpha"),
                product("p-4", "Gamma"),
            )
        val cards =
            listOf(
                card(), // productId "product-1" (not in this catalog slice)
                card().copy(id = "card-2", productId = "p-1"),
            )

        // Carded products drop out; duplicate carded ids are harmless; order is case-insensitive.
        assertEquals(listOf("alpha", "beta", "Gamma"), productsWithoutCards(products, cards).map { it.name })

        // Every product carded → empty picker options.
        val allCarded = products.map { card().copy(id = it.id, productId = it.id) }
        assertTrue(productsWithoutCards(products, allCarded).isEmpty())

        // No cards at all → everything is an option.
        assertEquals(4, productsWithoutCards(products, emptyList()).size)
    }

    /** #396 — the low-stock read's product ids mark rows; the summary counts display truth. */
    @Test
    fun toInventoryRows_marksLowStockIds_andSummaryCountsDisplayedCards() {
        val alcohol = card() // productId "product-1"
        val pads =
            alcohol.copy(
                id = "card-2",
                productId = "p-2",
                productName = "Pads",
                currentStock = 2,
            )

        // Empty/disjoint id sets mark nothing — the #391 default shape is unchanged.
        assertTrue(listOf(alcohol, pads).toInventoryRows().none { it.isLow })
        assertTrue(listOf(alcohol, pads).toInventoryRows(setOf("p-9")).none { it.isLow })

        // A marked product id flips exactly its row (match is by productId, not card id).
        val rows = listOf(alcohol, pads).toInventoryRows(setOf("p-2"))
        assertFalse(rows.first { it.id == "card-1" }.isLow)
        assertTrue(rows.first { it.id == "card-2" }.isLow)

        // All-low marks every row.
        assertTrue(listOf(alcohol, pads).toInventoryRows(setOf("product-1", "p-2")).all { it.isLow })

        // Summary: nothing surfaced when no displayed card is low or there are no cards;
        // the count names displayed cards, not raw response rows.
        assertNull(lowStockSummaryLine(listOf(alcohol), setOf("p-2")))
        assertNull(lowStockSummaryLine(emptyList(), setOf("product-1")))
        assertEquals("1 of 2 cards low on stock", lowStockSummaryLine(listOf(alcohol, pads), setOf("product-1", "p-9")))
    }

    /** #397 — the movements-history presentation: names, signed quantities, normalized notes. */
    @Test
    fun toMovementRows_resolvesNames_signedQuantities_andNormalizesNotes() {
        fun movement(
            productId: String,
            quantityChange: Int,
            notes: String? = null,
        ) = InventoryMovementResponse(
            id = "movement-$productId-$quantityChange",
            productId = productId,
            branchId = "branch-1",
            branchDayId = "day-1",
            reason = if (quantityChange < 0) InventoryMovementReason.TESTER else InventoryMovementReason.RESTOCK,
            quantityChange = quantityChange,
            movedBy = "user-1",
            movedAt = "2026-08-24T01:00:00Z",
            notes = notes,
        )

        // Empty history → no rows.
        assertTrue(emptyList<InventoryMovementResponse>().toMovementRows(emptyList()).isEmpty())

        // Names resolve from the displayed cards by productId; an unknown card falls back to
        // the raw id; backend order (movedAt DESC) is preserved untouched.
        val cards = listOf(card()) // productId "product-1" → "Alcohol"
        val rows =
            listOf(
                movement("product-1", 5),
                movement("ghost-product", -2, " demo draw "),
                movement("product-1", 0),
            ).toMovementRows(cards)
        assertEquals(listOf("+5", "-2", "0"), rows.map { it.reasonLine.substringAfterLast("· ") })
        assertEquals("Alcohol", rows[0].productName)
        assertEquals("ghost-product", rows[1].productName)
        assertTrue(rows[0].reasonLine.startsWith("Restock"))
        assertTrue(rows[1].reasonLine.startsWith("Tester"))
        assertTrue(rows[2].reasonLine.startsWith("Restock"))

        // Notes normalize: blank collapses to null, real text trims through.
        assertNull(rows[0].notes)
        assertEquals("demo draw", rows[1].notes)

        // Signed display form directly.
        assertEquals("+3", signedQuantityLine(3))
        assertEquals("-3", signedQuantityLine(-3))
    }
}
