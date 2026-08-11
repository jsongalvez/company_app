package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.UserBranchAssignment
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

object UserBranchAssignmentRepository {
    fun create(
        params: UserBranchAssignmentCreateParams,
        auditFn: (UserBranchAssignment) -> Unit = {},
    ): Boolean =
        transaction {
            val insertedCount =
                UserBranchAssignmentTable
                    .insertIgnore {
                        it[UserBranchAssignmentTable.id] = params.id
                        it[UserBranchAssignmentTable.userId] = params.userId
                        it[UserBranchAssignmentTable.branchId] = params.branchId
                        it[UserBranchAssignmentTable.slot] = params.slot
                        it[UserBranchAssignmentTable.assignedBy] = params.assignedBy
                    }.insertedCount
            val created = insertedCount > 0

            if (created) {
                val assignment =
                    UserBranchAssignmentTable
                        .selectAll()
                        .where { UserBranchAssignmentTable.id eq params.id }
                        .single()
                        .toAssignment()
                auditFn(assignment)
            }

            created
        }.also { created ->
            logger.info { "[CREATE-ASSIGNMENT] Assignment ${params.id} created=$created" }
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

    private fun findActiveByBranchAndUserInTransaction(
        branchId: UUID,
        userId: UUID,
        forUpdate: Boolean = false,
    ): UserBranchAssignment? {
        val query =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.branchId eq branchId) and
                        (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.endedAt.isNull())
                }
        val materialized = if (forUpdate) query.forUpdate(ForUpdateOption.ForUpdate) else query
        return materialized.singleOrNull()?.toAssignment()
    }

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
        auditFn: (UserBranchAssignment, UserBranchAssignment) -> Unit = { _, _ -> },
    ) {
        transaction {
            val before =
                UserBranchAssignmentTable
                    .selectAll()
                    .where { UserBranchAssignmentTable.id eq id }
                    .single()
                    .toAssignment()

            UserBranchAssignmentTable.update({ UserBranchAssignmentTable.id eq id }) {
                it[UserBranchAssignmentTable.endedAt] = CurrentTimestampWithTimeZone
            }

            val after =
                UserBranchAssignmentTable
                    .selectAll()
                    .where { UserBranchAssignmentTable.id eq id }
                    .single()
                    .toAssignment()
            auditFn(before, after)
        }
        logger.info { "[SET-ENDED-AT] Assignment ${id.toString().maskUUID()}" }
    }

    fun updateSlot(
        id: UUID,
        slot: Short,
        auditFn: (UserBranchAssignment, UserBranchAssignment) -> Unit = { _, _ -> },
    ) {
        transaction {
            val before =
                UserBranchAssignmentTable
                    .selectAll()
                    .where { UserBranchAssignmentTable.id eq id }
                    .single()
                    .toAssignment()

            UserBranchAssignmentTable.update({
                (UserBranchAssignmentTable.id eq id) and
                    (UserBranchAssignmentTable.endedAt.isNull())
            }) {
                it[UserBranchAssignmentTable.slot] = slot
            }

            val after =
                UserBranchAssignmentTable
                    .selectAll()
                    .where { UserBranchAssignmentTable.id eq id }
                    .single()
                    .toAssignment()
            auditFn(before, after)
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

    fun swapSlots(
        branchId: UUID,
        userIdA: UUID,
        userIdB: UUID,
        auditFn: (UserBranchAssignment, UserBranchAssignment) -> Unit = { _, _ -> },
    ): Pair<UserBranchAssignment, UserBranchAssignment> =
        transaction {
            // FOR UPDATE on both rows (materialized via singleOrNull — the #136 lazy-lock
            // lesson) serializes swap against concurrent remove/updateSlot on either user.
            val a =
                findActiveByBranchAndUserInTransaction(branchId, userIdA, forUpdate = true)
                    ?: throw NotFoundException("Active assignment not found for user A at this branch")
            val b =
                findActiveByBranchAndUserInTransaction(branchId, userIdB, forUpdate = true)
                    ?: throw NotFoundException("Active assignment not found for user B at this branch")

            val slotA = a.slot
            val slotB = b.slot

            swapSlotsInTransaction(a.id, slotA, b.id, slotB)

            auditFn(a, b)

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
