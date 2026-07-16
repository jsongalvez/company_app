package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object UserBranchAssignmentRepository {
    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        userId: UUID,
        branchId: UUID,
        slot: Short,
        assignedBy: UUID,
    ): Boolean =
        transaction {
            val insertedCount =
                UserBranchAssignmentTable
                    .insertIgnore {
                        it[UserBranchAssignmentTable.id] = id
                        it[UserBranchAssignmentTable.userId] = userId
                        it[UserBranchAssignmentTable.branchId] = branchId
                        it[UserBranchAssignmentTable.slot] = slot
                        it[UserBranchAssignmentTable.assignedBy] = assignedBy
                    }.insertedCount
            val created = insertedCount > 0

            if (created) {
                AuditLogRepository.record(
                    tableName = UserBranchAssignmentTable.tableName,
                    recordId = id,
                    action = AuditAction.INSERT,
                    changedBy = assignedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to id.toString(),
                            "userId" to userId.toString(),
                            "branchId" to branchId.toString(),
                            "slot" to slot.toString(),
                        ),
                )
            }

            created
        }.also { created ->
            logger.info { "[CREATE-ASSIGNMENT] Assignment $id created=$created" }
        }

    fun findActiveByBranch(branchId: UUID): List<UserBranchAssignment> =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.branchId eq branchId) and
                        (UserBranchAssignmentTable.endedAt.isNull())
                }.orderBy(
                    UserBranchAssignmentTable.slot to SortOrder.ASC,
                    UserBranchAssignmentTable.userId to SortOrder.ASC,
                ).map { it.toAssignment() }
        }.also { logger.info { "[FIND-ASSIGNMENTS-BY-BRANCH] Fetched ${it.size} active assignments" } }

    fun findActiveByBranchAndUserInTransaction(
        branchId: UUID,
        userId: UUID,
    ): UserBranchAssignment? =
        UserBranchAssignmentTable
            .selectAll()
            .where {
                (UserBranchAssignmentTable.branchId eq branchId) and
                    (UserBranchAssignmentTable.userId eq userId) and
                    (UserBranchAssignmentTable.endedAt.isNull())
            }.singleOrNull()
            ?.toAssignment()

    fun findActiveByBranchAndUser(
        branchId: UUID,
        userId: UUID,
    ): UserBranchAssignment? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.branchId eq branchId) and
                        (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.endedAt.isNull())
                }.singleOrNull()
                ?.toAssignment()
        }

    fun findById(id: UUID): UserBranchAssignment? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq id }
                .singleOrNull()
                ?.toAssignment()
        }.also { logger.info { "[FIND-ASSIGNMENT-BY-ID] id=${id.toString().maskUUID()} found=${it != null}" } }

    fun setEndedAt(
        id: UUID,
        callerId: UUID,
        oldValue: String,
    ) {
        transaction {
            UserBranchAssignmentTable.update({ UserBranchAssignmentTable.id eq id }) {
                it[UserBranchAssignmentTable.endedAt] = CurrentTimestampWithTimeZone
            }

            AuditLogRepository.record(
                tableName = UserBranchAssignmentTable.tableName,
                recordId = id,
                action = AuditAction.UPDATE,
                changedBy = callerId,
                oldValue = oldValue,
            )
        }
        logger.info { "[SET-ENDED-AT] Assignment ${id.toString().maskUUID()}" }
    }

    fun updateSlot(
        id: UUID,
        slot: Short,
        callerId: UUID,
        oldSlot: Short,
    ) {
        transaction {
            UserBranchAssignmentTable.update({
                (UserBranchAssignmentTable.id eq id) and
                    (UserBranchAssignmentTable.endedAt.isNull())
            }) {
                it[UserBranchAssignmentTable.slot] = slot
            }

            AuditLogRepository.record(
                tableName = UserBranchAssignmentTable.tableName,
                recordId = id,
                action = AuditAction.UPDATE,
                changedBy = callerId,
                oldValue = AuditLogRepository.jsonField("slot", oldSlot.toString()),
                newValue = AuditLogRepository.jsonField("slot", slot.toString()),
            )
        }
        logger.info { "[UPDATE-SLOT] Assignment ${id.toString().maskUUID()} slot=$slot" }
    }

    fun swapSlotsInTransaction(
        idA: UUID,
        slotA: Short,
        idB: UUID,
        slotB: Short,
    ) {
        UserBranchAssignmentTable.update({
            (UserBranchAssignmentTable.id eq idA) and
                (UserBranchAssignmentTable.endedAt.isNull())
        }) {
            it[UserBranchAssignmentTable.slot] = slotB
        }
        UserBranchAssignmentTable.update({
            (UserBranchAssignmentTable.id eq idB) and
                (UserBranchAssignmentTable.endedAt.isNull())
        }) {
            it[UserBranchAssignmentTable.slot] = slotA
        }
    }

    @Suppress("LongParameterList")
    fun swapSlots(
        callerId: UUID,
        branchId: UUID,
        userIdA: UUID,
        userIdB: UUID,
    ): Pair<UserBranchAssignment, UserBranchAssignment> =
        transaction {
            val a =
                findActiveByBranchAndUserInTransaction(branchId, userIdA)
                    ?: throw NotFoundResponse("Active assignment not found for user A at this branch")
            val b =
                findActiveByBranchAndUserInTransaction(branchId, userIdB)
                    ?: throw NotFoundResponse("Active assignment not found for user B at this branch")

            val slotA = a.slot
            val slotB = b.slot

            swapSlotsInTransaction(a.id, slotA, b.id, slotB)

            AuditLogRepository.record(
                tableName = UserBranchAssignmentTable.tableName,
                recordId = a.id,
                action = AuditAction.UPDATE,
                changedBy = callerId,
                newValue =
                    AuditLogRepository.jsonFields(
                        "userIdA" to userIdA.toString(),
                        "oldSlotA" to slotA.toString(),
                        "newSlotA" to slotB.toString(),
                        "userIdB" to userIdB.toString(),
                        "oldSlotB" to slotB.toString(),
                        "newSlotB" to slotA.toString(),
                    ),
            )

            a to b
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toAssignment(): UserBranchAssignment =
        UserBranchAssignment(
            id = this[UserBranchAssignmentTable.id],
            userId = this[UserBranchAssignmentTable.userId],
            branchId = this[UserBranchAssignmentTable.branchId],
            slot = this[UserBranchAssignmentTable.slot],
            assignedBy = this[UserBranchAssignmentTable.assignedBy],
            assignedAt = this[UserBranchAssignmentTable.assignedAt],
            endedAt = this[UserBranchAssignmentTable.endedAt],
        )
}
