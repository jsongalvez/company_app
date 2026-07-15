package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
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

    fun assignWithCapability(
        delegateId: UUID,
        targetUserId: UUID,
        assignedBy: UUID,
        branchId: UUID,
        capabilityId: UUID,
    ): Unit =
        transaction {
            MedicalMissionDelegateTable.insertIgnore {
                it[MedicalMissionDelegateTable.id] = delegateId
                it[MedicalMissionDelegateTable.targetUser] = targetUserId
                it[MedicalMissionDelegateTable.assignedBy] = assignedBy
                it[MedicalMissionDelegateTable.branchId] = branchId
            }

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

    fun revokeWithCapability(delegateId: UUID): Unit =
        transaction {
            MedicalMissionDelegateTable
                .update({ MedicalMissionDelegateTable.id eq delegateId }) {
                    it[MedicalMissionDelegateTable.endedAt] = OffsetDateTime.now()
                }

            UserCapabilityTable
                .update({
                    (UserCapabilityTable.sourceId eq delegateId) and
                        (UserCapabilityTable.validTo.isNull())
                }) {
                    it[UserCapabilityTable.validTo] = OffsetDateTime.now()
                }
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toMedicalMissionDelegate(): MedicalMissionDelegate =
        MedicalMissionDelegate(
            id = this[MedicalMissionDelegateTable.id],
            targetUser = this[MedicalMissionDelegateTable.targetUser],
            assignedAt = this[MedicalMissionDelegateTable.assignedAt],
            assignedBy = this[MedicalMissionDelegateTable.assignedBy],
            branchId = this[MedicalMissionDelegateTable.branchId],
            endedAt = this[MedicalMissionDelegateTable.endedAt],
        )
}
