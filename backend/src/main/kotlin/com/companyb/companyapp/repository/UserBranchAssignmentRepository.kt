package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.ConflictException
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

data class AssignmentCreateResult(
    val assignment: UserBranchAssignment,
    val created: Boolean,
)

/** Before/after projection for single-assignment mutations (#323). */
data class AssignmentMutation(
    val before: UserBranchAssignment,
    val after: UserBranchAssignment,
)

@Suppress("TooManyFunctions")
object UserBranchAssignmentRepository {
    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createInTransaction(params: UserBranchAssignmentCreateParams): AssignmentCreateResult {
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
            return AssignmentCreateResult(assignment, created = true)
        }

        val sameIdExists =
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq params.id }
                .empty()
                .not()
        // Same-ID retries are idempotent. A different row means the active
        // business key won the race, so expose a deterministic domain error.
        if (!sameIdExists) {
            throw ConflictException("User already has an active assignment at this branch")
        }
        val existing = findByIdInTransaction(params.id) ?: error("Assignment not found after create for ${params.id}")
        return AssignmentCreateResult(existing, created = false)
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
        forUpdate: Boolean,
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

    fun findActiveByIdInTransaction(
        branchId: UUID,
        assignmentId: UUID,
        forUpdate: Boolean,
    ): UserBranchAssignment? {
        val query =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.id eq assignmentId) and
                        (UserBranchAssignmentTable.branchId eq branchId) and
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
            findActiveByBranchAndUserInTransaction(branchId, userId, forUpdate = false)
        }

    fun findById(id: UUID): UserBranchAssignment? =
        transaction {
            findByIdInTransaction(id)
        }.also { logger.info { "[FIND-ASSIGNMENT-BY-ID] id=${id.toString().maskUUID()} found=${it != null}" } }

    private fun findByIdInTransaction(id: UUID): UserBranchAssignment? =
        UserBranchAssignmentTable
            .selectAll()
            .where { UserBranchAssignmentTable.id eq id }
            .singleOrNull()
            ?.toAssignment()

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun setEndedAtInTransaction(id: UUID): AssignmentMutation {
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
        return AssignmentMutation(before, after)
    }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun updateSlotInTransaction(
        id: UUID,
        slot: Short,
    ): AssignmentMutation {
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
        return AssignmentMutation(before, after)
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
