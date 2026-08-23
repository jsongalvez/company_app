package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.BranchInventoryResponse
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
}
