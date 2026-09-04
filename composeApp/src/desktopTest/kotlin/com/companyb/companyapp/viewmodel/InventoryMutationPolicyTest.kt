package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// #458 — the mutation-policy effect wiring: one busy predicate, one banner-error source, and
// one row-affordance gate, all fail-closed. The capability/validation/builder surface moved
// here verbatim stays pinned by InventoryWriteLogicTest + ProductSaleLogicTest.
class InventoryMutationPolicyTest {
    private fun cap(
        code: String,
        contextType: CapabilityContextType,
        contextId: String,
    ) = UserCapabilityResponse(code, contextType, contextId, CapabilitySourceType.ROLE)

    private val branchId = "branch-1"
    private val dayId = "day-1"

    private val manager =
        listOf(
            cap("MANAGE_PRODUCTS", CapabilityContextType.BRANCH, branchId),
            cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, branchId),
        )

    @Test
    fun writesDisabled_trueWhenAnyWriteLegLoading_cardLegExcluded() {
        assertFalse(writesDisabled(UiState.Idle, UiState.Idle, UiState.Idle))
        assertTrue(writesDisabled(UiState.Loading, UiState.Idle, UiState.Idle))
        assertTrue(writesDisabled(UiState.Idle, UiState.Loading, UiState.Idle))
        assertTrue(writesDisabled(UiState.Idle, UiState.Idle, UiState.Loading))
        // Success legs never disable; the ensure-card leg rides its own header disablement.
        assertFalse(
            writesDisabled(
                UiState.Success(Unit),
                UiState.Error("nope"),
                UiState.Success(Unit),
            ),
        )
    }

    @Test
    fun firstWriteError_returnsFirstInLegOrder_nullWhenClean() {
        assertNull(firstWriteError(UiState.Idle, UiState.Idle, UiState.Idle, UiState.Idle))
        val restock = UiState.Error("restock")
        val movement = UiState.Error("movement")
        val card = UiState.Error("card")
        val sale = UiState.Error("sale")
        assertEquals(restock, firstWriteError(restock, movement, card, sale))
        assertEquals(movement, firstWriteError(UiState.Idle, movement, card, sale))
        assertEquals(card, firstWriteError(UiState.Idle, UiState.Idle, card, sale))
        assertEquals(sale, firstWriteError(UiState.Idle, UiState.Idle, UiState.Idle, sale))
    }

    @Test
    fun rowActions_failClosedWithoutDayBranchOrWhileBusy() {
        val open = inventoryRowActions(manager, branchId, dayId, writesDisabled = false)
        assertTrue(open.restockEnabled && open.movementEnabled && open.sellEnabled)

        // No clocked-in day → every arm hides, even for a fully capable caller.
        val noDay = inventoryRowActions(manager, branchId, null, writesDisabled = false)
        assertFalse(noDay.restockEnabled || noDay.movementEnabled || noDay.sellEnabled)

        // No branch → every arm hides (the capability matchers are exact-scope).
        val noBranch = inventoryRowActions(manager, null, dayId, writesDisabled = false)
        assertFalse(noBranch.restockEnabled || noBranch.movementEnabled || noBranch.sellEnabled)

        // Any in-flight write disables every arm.
        val busy = inventoryRowActions(manager, branchId, dayId, writesDisabled = true)
        assertFalse(busy.restockEnabled || busy.movementEnabled || busy.sellEnabled)

        // No capabilities → nothing, even with a day and no contention.
        val bare = inventoryRowActions(emptyList(), branchId, dayId, writesDisabled = false)
        assertFalse(bare.restockEnabled || bare.movementEnabled || bare.sellEnabled)
    }

    @Test
    fun rowActions_splitByCapability_exactlyLikeTheBackendRouteFilters() {
        val editor = listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, branchId))
        val editorActions = inventoryRowActions(editor, branchId, dayId, writesDisabled = false)
        assertFalse(editorActions.restockEnabled)
        assertTrue(editorActions.movementEnabled)
        assertTrue(editorActions.sellEnabled)

        // A day grant alone sells (the relief leg) but never restocks or records movements.
        val relief = listOf(cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, dayId))
        val reliefActions = inventoryRowActions(relief, branchId, dayId, writesDisabled = false)
        assertFalse(reliefActions.restockEnabled)
        assertFalse(reliefActions.movementEnabled)
        assertTrue(reliefActions.sellEnabled)
    }
}
