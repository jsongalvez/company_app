package com.companyb.companyapp.service.inventory

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

    companion object {
        fun fromReason(reason: String): MovementType =
            when (reason.uppercase()) {
                "RESTOCK" -> Restock
                "SALE" -> Sale
                "TESTER" -> Tester
                "SAMPLE" -> Sample
                "MISSING" -> Missing
                "ADJUSTMENT" -> Adjustment
                else -> throw IllegalArgumentException("Unknown movement type: $reason")
            }
    }
}
