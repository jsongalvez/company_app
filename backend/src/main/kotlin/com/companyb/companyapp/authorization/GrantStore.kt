package com.companyb.companyapp.authorization

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

/** The shared relief-grant write (#159 Q1): who gets the day, for which day, from which source. */
data class GrantReliefCapabilityParams(
    val userId: UUID,
    val branchDayId: UUID,
    val sourceId: UUID,
    val validTo: OffsetDateTime?,
)

/**
 * Grant persistence (#538): the only writer of `user_capability` rows outside test
 * fixtures. Workforce commands (relief request grant, invite accept/revoke, delegate
 * assign/revoke) reach these rows only through [AuthorizationGrants] in their own
 * command transaction — never by importing the table.
 */
internal object GrantStore {
    /**
     * The shared relief-grant writer (#159 Q1, moved from the workforce relief store):
     * inserts the day-scoped capability (EDIT_BRANCH_DATA, BRANCH_DAY, [params.branchDayId],
     * source RELIEF_ACCESS, priority [GrantPriorities.RELIEF_ACCESS], validFrom = now, validTo =
     * [GrantReliefCapabilityParams.validTo]) — the capability write used by BOTH the
     * request flow's grant and the invite flow's accept. `insertIgnore` keeps a redundant
     * grant harmless (the #159 Q3 decision: capabilities ≠ assignments).
     *
     * In-transaction store operation (ADR-0024) — runs on the caller's command
     * transaction.
     */
    fun grantReliefCapabilityInTransaction(params: GrantReliefCapabilityParams) {
        UserCapabilityTable.insertIgnore {
            it[UserCapabilityTable.userId] = params.userId
            it[UserCapabilityTable.capabilityId] = reliefCapabilityId()
            it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH_DAY
            it[UserCapabilityTable.contextId] = params.branchDayId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.RELIEF_ACCESS
            it[UserCapabilityTable.sourceId] = params.sourceId
            it[UserCapabilityTable.validFrom] = CurrentTimestampWithTimeZone
            it[UserCapabilityTable.validTo] = params.validTo
            it[UserCapabilityTable.priority] = GrantPriorities.RELIEF_ACCESS
        }
    }

    /**
     * Grant removal keyed on the capability's sourceId (#374, moved): deletes every
     * RELIEF_ACCESS-sourced capability row minted from [sourceId] for [userId] (an
     * invite id — accept writes exactly one row for the invitee; the user scope keeps a
     * pathological id collision from ever deleting another holder's grant).
     * In-transaction store operation (ADR-0024) — runs inside the revoke command's
     * transaction so the status flip and grant removal commit or roll back together.
     */
    fun deleteReliefGrantBySourceIdInTransaction(
        userId: UUID,
        sourceId: UUID,
    ) {
        UserCapabilityTable.deleteWhere {
            (UserCapabilityTable.userId eq userId) and
                (UserCapabilityTable.sourceType eq CapabilitySourceType.RELIEF_ACCESS) and
                (UserCapabilityTable.sourceId eq sourceId)
        }
    }

    /**
     * Delegate-grant writer (moved from the workforce delegate store): inserts the
     * branch-scoped capability for [targetUserId] at [branchId], sourced from [delegateId].
     * In-transaction store operation (ADR-0024) — runs on the caller's command transaction
     * so the grant cannot outlive a failed delegate insert.
     */
    fun grantDelegateCapabilityInTransaction(
        targetUserId: UUID,
        branchId: UUID,
        delegateId: UUID,
        capabilityId: UUID,
    ) {
        UserCapabilityTable.insert {
            it[UserCapabilityTable.userId] = targetUserId
            it[UserCapabilityTable.capabilityId] = capabilityId
            it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH
            it[UserCapabilityTable.contextId] = branchId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.MEDICAL_MISSION_DELEGATE
            it[UserCapabilityTable.sourceId] = delegateId
            it[UserCapabilityTable.priority] = GrantPriorities.MEDICAL_MISSION_DELEGATE
        }
    }

    /**
     * Delegate-grant close (moved): ends the capability window opened by
     * [grantDelegateCapabilityInTransaction] when the delegate is revoked.
     * In-transaction store operation (ADR-0024).
     */
    fun closeDelegateCapabilityInTransaction(delegateId: UUID) {
        UserCapabilityTable
            .update({
                (UserCapabilityTable.sourceType eq CapabilitySourceType.MEDICAL_MISSION_DELEGATE) and
                    (UserCapabilityTable.sourceId eq delegateId) and
                    (UserCapabilityTable.validTo.isNull())
            }) {
                it[UserCapabilityTable.validTo] = CurrentTimestampWithTimeZone
            }
    }

    private fun reliefCapabilityId(): UUID =
        checkNotNull(
            CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
        ) { "EDIT_BRANCH_DATA capability not found" }
}
