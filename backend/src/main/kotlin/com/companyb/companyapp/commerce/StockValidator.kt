package com.companyb.companyapp.commerce

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.ValidationException
import java.util.UUID

internal object StockValidator {
    /**
     * Day gate + quantity validation for inventory movements (#454). Must be called
     * inside the caller's command transaction: the day row is locked
     * ([checkBranchDayEditableInTransaction]) so the gate serializes with
     * remittance's REMITTED transition instead of racing it.
     *
     * #597: 6-param movement gate stays whole per #535; caller passes the command's day + delta through.
     */
    @Suppress("LongParameterList") // #597
    fun validateMovement(
        callerId: UUID,
        branchDayId: UUID,
        movementType: MovementType,
        quantityChange: Int,
        notes: String?,
        reason: String? = null,
    ): Boolean {
        val (_, isRemitted) = BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDayId, reason)

        requireSign(movementType, quantityChange)
        requireNonZeroQuantity(quantityChange)
        requireMissingNotes(movementType, notes)

        return isRemitted
    }

    private fun requireSign(
        movementType: MovementType,
        quantityChange: Int,
    ) {
        val sign = quantityChange.compareTo(0)
        when (movementType.signRequired) {
            MovementType.Sign.POSITIVE -> {
                if (sign <= 0) throw ValidationException("$movementType must have a positive quantity change")
            }

            MovementType.Sign.NEGATIVE -> {
                if (sign >= 0) throw ValidationException("$movementType must have a negative quantity change")
            }

            MovementType.Sign.ANY -> { /* either sign allowed */ }
        }
    }

    /**
     * Zero-quantity movements are no-ops that would still bump the card version and
     * write audit rows — and violate the `quantity_change != 0` CHECK at the DB.
     * Rejected here so the caller gets a 400 instead of a 500.
     */
    private fun requireNonZeroQuantity(quantityChange: Int) {
        if (quantityChange == 0) throw ValidationException("Quantity change must not be zero")
    }

    private fun requireMissingNotes(
        movementType: MovementType,
        notes: String?,
    ) {
        if (movementType is MovementType.Missing && notes.isNullOrBlank()) {
            throw ValidationException("Notes are required for MISSING movements")
        }
    }
}
