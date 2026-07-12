package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
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
            insertedCount > 0
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
        endedAt: OffsetDateTime,
    ) {
        transaction {
            UserBranchAssignmentTable.update({ UserBranchAssignmentTable.id eq id }) {
                it[UserBranchAssignmentTable.endedAt] = endedAt
            }
        }
        logger.info { "[SET-ENDED-AT] Assignment ${id.toString().maskUUID()}" }
    }

    fun updateSlot(
        id: UUID,
        slot: Short,
    ) {
        transaction {
            UserBranchAssignmentTable.update({
                (UserBranchAssignmentTable.id eq id) and
                    (UserBranchAssignmentTable.endedAt.isNull())
            }) {
                it[UserBranchAssignmentTable.slot] = slot
            }
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

    private fun org.jetbrains.exposed.sql.ResultRow.toAssignment(): UserBranchAssignment =
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
