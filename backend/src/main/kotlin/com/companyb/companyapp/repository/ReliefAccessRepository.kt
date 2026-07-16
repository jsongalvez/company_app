package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.ReliefStatus
import com.companyb.companyapp.repository.model.UserCapabilityTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

object ReliefAccessRepository {
    fun findById(id: UUID): ReliefAccess? =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq id }
                .singleOrNull()
                ?.toReliefAccess()
        }

    fun findByRequestedByAndBranchDayId(
        requestedBy: UUID,
        branchDayId: UUID,
        status: ReliefStatus,
    ): ReliefAccess? =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where {
                    (GrantReliefAccessTable.requestedBy eq requestedBy) and
                        (GrantReliefAccessTable.branchDayId eq branchDayId) and
                        (GrantReliefAccessTable.requestStatus eq status)
                }.singleOrNull()
                ?.toReliefAccess()
        }

    @Suppress("LongParameterList")
    fun grantWithCapability(
        requestId: UUID,
        grantedBy: UUID,
        userId: UUID,
        capabilityId: UUID,
        branchDayId: UUID,
        sourceId: UUID,
        validTo: OffsetDateTime?,
        priority: Short,
        requestedBy: UUID,
    ): Unit =
        transaction {
            GrantReliefAccessTable
                .update({ GrantReliefAccessTable.id eq requestId }) {
                    it[GrantReliefAccessTable.requestStatus] = ReliefStatus.GRANTED
                    it[GrantReliefAccessTable.grantedBy] = grantedBy
                    it[GrantReliefAccessTable.grantedAt] = CurrentTimestampWithTimeZone
                }
            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capabilityId
                it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH_DAY
                it[UserCapabilityTable.contextId] = branchDayId
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.RELIEF_ACCESS
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.validTo] = validTo
                it[UserCapabilityTable.priority] = priority
            }

            AuditLogRepository.record(
                tableName = GrantReliefAccessTable.tableName,
                recordId = requestId,
                action = AuditAction.UPDATE,
                changedBy = grantedBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "requestId" to requestId.toString(),
                        "branchDayId" to branchDayId.toString(),
                        "grantedBy" to grantedBy.toString(),
                        "requestedBy" to requestedBy.toString(),
                    ),
            )
        }

    fun deny(
        requestId: UUID,
        callerId: UUID,
    ) = transaction {
        GrantReliefAccessTable
            .update({ GrantReliefAccessTable.id eq requestId }) {
                it[GrantReliefAccessTable.requestStatus] = ReliefStatus.DENIED
            }

        AuditLogRepository.record(
            tableName = GrantReliefAccessTable.tableName,
            recordId = requestId,
            action = AuditAction.UPDATE,
            changedBy = callerId,
            newValue =
                AuditLogRepository.jsonFields(
                    "requestId" to requestId.toString(),
                    "status" to "DENIED",
                ),
        )
    }

    fun insertRequest(
        id: UUID,
        branchDayId: UUID,
        requestedBy: UUID,
        targetUser: UUID,
    ): Pair<ReliefAccess, Boolean> =
        transaction {
            val insertedCount =
                GrantReliefAccessTable
                    .insertIgnore {
                        it[GrantReliefAccessTable.id] = id
                        it[GrantReliefAccessTable.branchDayId] = branchDayId
                        it[GrantReliefAccessTable.requestedBy] = requestedBy
                        it[GrantReliefAccessTable.targetUser] = targetUser
                    }.insertedCount
            val isNew = insertedCount > 0

            if (isNew) {
                AuditLogRepository.record(
                    tableName = GrantReliefAccessTable.tableName,
                    recordId = id,
                    action = AuditAction.INSERT,
                    changedBy = requestedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "requestId" to id.toString(),
                            "branchDayId" to branchDayId.toString(),
                            "targetUserId" to targetUser.toString(),
                        ),
                )
            }

            val row =
                GrantReliefAccessTable
                    .selectAll()
                    .where { GrantReliefAccessTable.id eq id }
                    .single()
                    .toReliefAccess()

            row to isNew
        }

    fun hasActiveClockIn(
        targetUser: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            com.companyb.companyapp.repository.model.AttendanceTable
                .selectAll()
                .where {
                    (com.companyb.companyapp.repository.model.AttendanceTable.userId eq targetUser) and
                        (com.companyb.companyapp.repository.model.AttendanceTable.branchDayId eq branchDayId) and
                        (
                            com.companyb.companyapp.repository.model.AttendanceTable.clockOut
                                .isNull()
                        )
                }.empty()
                .not()
        }

    fun isReliefUser(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            com.companyb.companyapp.repository.model.BranchDayAssignmentTable
                .selectAll()
                .where {
                    (com.companyb.companyapp.repository.model.BranchDayAssignmentTable.userId eq userId) and
                        (com.companyb.companyapp.repository.model.BranchDayAssignmentTable.branchDayId eq branchDayId)
                }.singleOrNull()
                ?.let { it[com.companyb.companyapp.repository.model.BranchDayAssignmentTable.isRelief] }
                ?: false
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toReliefAccess(): ReliefAccess =
        ReliefAccess(
            id = this[GrantReliefAccessTable.id],
            branchDayId = this[GrantReliefAccessTable.branchDayId],
            requestedBy = this[GrantReliefAccessTable.requestedBy],
            requestStatus = this[GrantReliefAccessTable.requestStatus],
            targetUser = this[GrantReliefAccessTable.targetUser],
            grantedBy = this[GrantReliefAccessTable.grantedBy],
            grantedAt = this[GrantReliefAccessTable.grantedAt],
        )
}
