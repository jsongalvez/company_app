package com.companyb.companyapp.authorization

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import java.time.OffsetDateTime
import java.util.UUID

/** The shared relief-grant write: who gets the day, for which day, from which source. */
data class GrantReliefCapabilityParams(
    val userId: UUID,
    val branchDayId: UUID,
    val sourceId: UUID,
    val validTo: OffsetDateTime?,
)

internal object GrantStore {
    /** Writes [params] as a relief day; granting the same day from the same source twice is harmless. */
    fun grantReliefCapabilityInTransaction(params: GrantReliefCapabilityParams) {
        ReliefDayTable.insertIgnore {
            it[ReliefDayTable.userId] = params.userId
            it[ReliefDayTable.branchDayId] = params.branchDayId
            it[ReliefDayTable.sourceId] = params.sourceId
            it[ReliefDayTable.validTo] = params.validTo
        }
    }

    /** Removes the relief days [sourceId] granted [userId], in the revoking command's transaction. */
    fun deleteReliefGrantBySourceIdInTransaction(
        userId: UUID,
        sourceId: UUID,
    ) {
        ReliefDayTable.deleteWhere {
            (ReliefDayTable.userId eq userId) and (ReliefDayTable.sourceId eq sourceId)
        }
    }
}
