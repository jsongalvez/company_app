package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.workforce.DelegateRole
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

    /**
     * Delegate-grant writer (moved from the workforce delegate store): inserts the
     * branch-scoped capability for [targetUserId] at [branchId], sourced from [delegateId].
     * Owns the fixed EDIT_BRANCH_DATA capability code/ID, BRANCH context, delegate source
     * and priority — callers supply only target user, branch and delegate identity.
     * In-transaction store operation (ADR-0024) — runs on the caller's command transaction
     * so the grant cannot outlive a failed delegate insert.
     */
    fun grantDelegateCapabilityInTransaction(
        targetUserId: UUID,
        branchId: UUID,
        delegateId: UUID,
        role: DelegateRole = DelegateRole.PRACTITIONER,
    ) {
        helperCodes(role).forEach { code ->
            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = targetUserId
                it[UserCapabilityTable.capabilityId] = capabilityId(code)
                it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH
                it[UserCapabilityTable.contextId] = branchId
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.MEDICAL_MISSION_DELEGATE
                it[UserCapabilityTable.sourceId] = delegateId
                it[UserCapabilityTable.priority] = GrantPriorities.MEDICAL_MISSION_DELEGATE
            }
        }
    }

    /**
     * EDIT_BRANCH_DATA carries a member's rights ([Policy.grants]); a
     * Coordinator's branch rights come on top.
     */
    private fun helperCodes(role: DelegateRole): List<String> =
        when (role) {
            DelegateRole.PRACTITIONER -> {
                listOf(CapabilityCodes.EDIT_BRANCH_DATA)
            }

            DelegateRole.COORDINATOR -> {
                listOf(
                    CapabilityCodes.EDIT_BRANCH_DATA,
                    CapabilityCodes.EDIT_PAST_DAY,
                    CapabilityCodes.SUBMIT_REMITTANCE,
                    CapabilityCodes.ASSIGN_COMPENSATION,
                    CapabilityCodes.MANAGE_PRODUCTS,
                    CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS,
                )
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

    private fun capabilityId(code: String): UUID =
        checkNotNull(CapabilityRepository.findIdByCode(code)) { "$code capability not found" }
}
