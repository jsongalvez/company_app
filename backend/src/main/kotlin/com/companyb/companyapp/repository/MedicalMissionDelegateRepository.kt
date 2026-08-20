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

object MedicalMissionDelegateRepository {
    fun findById(id: UUID): MedicalMissionDelegate? =
        transaction {
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.id eq id }
                .singleOrNull()
                ?.toMedicalMissionDelegate()
        }

    @Suppress("LongParameterList")
    fun assignWithCapability(
        delegateId: UUID,
        targetUserId: UUID,
        assignedBy: UUID,
        branchId: UUID,
        capabilityId: UUID,
        auditFn: (MedicalMissionDelegate) -> Unit = {},
    ): Unit =
        transaction {
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
            if (inserted) {
                auditFn(delegate)
            }
        }

    fun revokeWithCapability(
        delegateId: UUID,
        auditFn: (MedicalMissionDelegate) -> Unit = {},
    ): Unit =
        transaction {
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

            val revoked =
                MedicalMissionDelegateTable
                    .selectAll()
                    .where { MedicalMissionDelegateTable.id eq delegateId }
                    .single()
                    .toMedicalMissionDelegate()
            auditFn(revoked)
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
