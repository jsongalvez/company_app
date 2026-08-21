package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Store result for delegate assign (#323): `inserted=false` marks an idempotent same-id retry. */
data class DelegateAssignResult(
    val delegate: MedicalMissionDelegate,
    val inserted: Boolean,
)

object MedicalMissionDelegateRepository {
    fun findById(id: UUID): MedicalMissionDelegate? =
        transaction {
            findByIdInTransaction(id)
        }

    fun findByIdInTransaction(id: UUID): MedicalMissionDelegate? =
        MedicalMissionDelegateTable
            .selectAll()
            .where { MedicalMissionDelegateTable.id eq id }
            .singleOrNull()
            ?.toMedicalMissionDelegate()

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. The capability insert stays inside so it cannot outlive a failed
     * delegate insert.
     */
    fun assignInTransaction(
        delegateId: UUID,
        targetUserId: UUID,
        assignedBy: UUID,
        branchId: UUID,
        capabilityId: UUID,
    ): DelegateAssignResult {
        val inserted =
            MedicalMissionDelegateTable
                .insertIgnore {
                    it[MedicalMissionDelegateTable.id] = delegateId
                    it[MedicalMissionDelegateTable.targetUser] = targetUserId
                    it[MedicalMissionDelegateTable.assignedBy] = assignedBy
                    it[MedicalMissionDelegateTable.branchId] = branchId
                }.insertedCount > 0

        if (inserted) {
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

        val delegate =
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.id eq delegateId }
                .single()
                .toMedicalMissionDelegate()
        return DelegateAssignResult(delegate, inserted)
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. The endedAt update and the capability-window close stay inside so
     * neither commits without the other.
     */
    fun revokeInTransaction(delegateId: UUID): MedicalMissionDelegate {
        MedicalMissionDelegateTable
            .update({ MedicalMissionDelegateTable.id eq delegateId }) {
                it[MedicalMissionDelegateTable.endedAt] = CurrentTimestampWithTimeZone
            }

        UserCapabilityTable
            .update({
                (UserCapabilityTable.sourceId eq delegateId) and
                    (UserCapabilityTable.validTo.isNull())
            }) {
                it[UserCapabilityTable.validTo] = CurrentTimestampWithTimeZone
            }

        return MedicalMissionDelegateTable
            .selectAll()
            .where { MedicalMissionDelegateTable.id eq delegateId }
            .single()
            .toMedicalMissionDelegate()
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toMedicalMissionDelegate(): MedicalMissionDelegate =
        MedicalMissionDelegate(
            id = this[MedicalMissionDelegateTable.id],
            targetUser = this[MedicalMissionDelegateTable.targetUser],
            assignedAt = this[MedicalMissionDelegateTable.assignedAt],
            assignedBy = this[MedicalMissionDelegateTable.assignedBy],
            branchId = this[MedicalMissionDelegateTable.branchId],
            endedAt = this[MedicalMissionDelegateTable.endedAt],
        )
}
