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
     */
    @Suppress("ThrowsCount", "LongParameterList")
    fun validateMovement(
        callerId: UUID,
        branchDayId: UUID,
        movementType: MovementType,
        quantityChange: Int,
        notes: String?,
        reason: String? = null,
    ): Boolean {
        val (_, isRemitted) = BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDayId, reason)

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

        if (movementType is MovementType.Missing && notes.isNullOrBlank()) {
            throw ValidationException("Notes are required for MISSING movements")
        }

        return isRemitted
    }
}
