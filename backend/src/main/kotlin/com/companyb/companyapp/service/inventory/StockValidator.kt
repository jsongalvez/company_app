package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.service.branchday.BranchDayService
import java.util.UUID

internal object StockValidator {
    @Suppress("ThrowsCount", "LongParameterList")
    fun validateMovement(
        callerId: UUID,
        branchDayId: UUID,
        movementType: MovementType,
        quantityChange: Int,
        notes: String?,
        reason: String? = null,
    ): Boolean {
        val (_, isRemitted) = BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

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
