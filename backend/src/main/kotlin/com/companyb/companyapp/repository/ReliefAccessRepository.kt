package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.ReliefStatus
import com.companyb.companyapp.repository.model.UserCapabilityTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class GrantWithCapabilityParams(
    val requestId: UUID,
    val grantedBy: UUID,
    val userId: UUID,
    val branchDayId: UUID,
    val sourceId: UUID,
    val validTo: OffsetDateTime?,
    val requestedBy: UUID,
)

@Suppress("TooManyFunctions")
object ReliefAccessRepository {
    /**
     * The shared relief-grant writer (#159 Q1): inserts the day-scoped capability
     * (EDIT_BRANCH_DATA, BRANCH_DAY, [branchDayId], source RELIEF_ACCESS, priority
     * [GrantPriorities.RELIEF_ACCESS], validFrom = now, validTo = [validTo]) — the
     * capability write used by BOTH the request flow's grant ([grantWithCapability])
     * and the invite flow's accept. `insertIgnore` keeps a redundant grant harmless
     * (the #159 Q3 decision: capabilities ≠ assignments).
     */
    fun grantReliefCapability(
        userId: UUID,
        branchDayId: UUID,
        sourceId: UUID,
        validTo: OffsetDateTime?,
    ): Unit =
        transaction {
            insertReliefCapabilityInTransaction(userId, branchDayId, sourceId, validTo)
        }

    private fun insertReliefCapabilityInTransaction(
        userId: UUID,
        branchDayId: UUID,
        sourceId: UUID,
        validTo: OffsetDateTime?,
    ) {
        UserCapabilityTable.insertIgnore {
            it[UserCapabilityTable.userId] = userId
            it[UserCapabilityTable.capabilityId] = reliefCapabilityId()
            it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH_DAY
            it[UserCapabilityTable.contextId] = branchDayId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.RELIEF_ACCESS
            it[UserCapabilityTable.sourceId] = sourceId
            it[UserCapabilityTable.validFrom] = OffsetDateTime.now(ZoneOffset.UTC)
            it[UserCapabilityTable.validTo] = validTo
            it[UserCapabilityTable.priority] = GrantPriorities.RELIEF_ACCESS
        }
    }

    private fun reliefCapabilityId(): UUID =
        checkNotNull(
            CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
        ) { "EDIT_BRANCH_DATA capability not found" }

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

    fun grantWithCapability(
        params: GrantWithCapabilityParams,
        auditFn: (ReliefAccess, ReliefAccess) -> Unit = { _, _ -> },
    ): ReliefAccess? =
        transaction {
            val before =
                GrantReliefAccessTable
                    .selectAll()
                    .where { GrantReliefAccessTable.id eq params.requestId }
                    .singleOrNull()
                    ?.toReliefAccess()

            GrantReliefAccessTable
                .selectAll()
                .where {
                    (GrantReliefAccessTable.requestedBy eq params.requestedBy) and
                        (GrantReliefAccessTable.branchDayId eq params.branchDayId)
                }.forUpdate(ForUpdateOption.ForUpdate)
                .toList()

            val existingGrant =
                GrantReliefAccessTable
                    .selectAll()
                    .where {
                        (GrantReliefAccessTable.requestedBy eq params.requestedBy) and
                            (GrantReliefAccessTable.branchDayId eq params.branchDayId) and
                            (GrantReliefAccessTable.requestStatus eq ReliefStatus.GRANTED)
                    }.singleOrNull()
                    ?.toReliefAccess()

            if (existingGrant != null) {
                return@transaction existingGrant
            }

            GrantReliefAccessTable
                .update({ GrantReliefAccessTable.id eq params.requestId }) {
                    it[GrantReliefAccessTable.requestStatus] = ReliefStatus.GRANTED
                    it[GrantReliefAccessTable.grantedBy] = params.grantedBy
                    it[GrantReliefAccessTable.grantedAt] = CurrentTimestampWithTimeZone
                }

            insertReliefCapabilityInTransaction(
                userId = params.userId,
                branchDayId = params.branchDayId,
                sourceId = params.sourceId,
                validTo = params.validTo,
            )

            val after =
                GrantReliefAccessTable
                    .selectAll()
                    .where { GrantReliefAccessTable.id eq params.requestId }
                    .single()
                    .toReliefAccess()

            if (before != null) {
                auditFn(before, after)
            }
            after
        }

    fun deny(
        requestId: UUID,
        auditFn: (ReliefAccess, ReliefAccess) -> Unit = { _, _ -> },
    ) = transaction {
        val before =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .single()
                .toReliefAccess()

        GrantReliefAccessTable
            .update({ GrantReliefAccessTable.id eq requestId }) {
                it[GrantReliefAccessTable.requestStatus] = ReliefStatus.DENIED
            }

        val after =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .single()
                .toReliefAccess()

        auditFn(before, after)
    }

    fun insertRequest(
        id: UUID,
        branchDayId: UUID,
        requestedBy: UUID,
        targetUser: UUID,
        auditFn: (ReliefAccess) -> Unit = {},
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

            val row =
                GrantReliefAccessTable
                    .selectAll()
                    .where { GrantReliefAccessTable.id eq id }
                    .single()
                    .toReliefAccess()

            if (isNew) {
                auditFn(row)
            }

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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toReliefAccess(): ReliefAccess =
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
