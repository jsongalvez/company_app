package com.companyb.companyapp.commerce

import com.companyb.companyapp.contracts.commerce.InventoryMovementReason

sealed class MovementType(
    val signRequired: Sign,
) {
    enum class Sign { POSITIVE, NEGATIVE, ANY }

    data object Restock : MovementType(Sign.POSITIVE)

    data object Sale : MovementType(Sign.NEGATIVE)

    data object Tester : MovementType(Sign.NEGATIVE)

    data object Sample : MovementType(Sign.NEGATIVE)

    data object Missing : MovementType(Sign.NEGATIVE) {
        val notesRequired get() = true
    }

    data object Adjustment : MovementType(Sign.ANY)

    fun toInventoryMovementReason(): InventoryMovementReason =
        when (this) {
            Restock -> InventoryMovementReason.RESTOCK
            Sale -> InventoryMovementReason.SALE
            Tester -> InventoryMovementReason.TESTER
            Sample -> InventoryMovementReason.SAMPLE
            Missing -> InventoryMovementReason.MISSING
            Adjustment -> InventoryMovementReason.ADJUSTMENT
        }
}
