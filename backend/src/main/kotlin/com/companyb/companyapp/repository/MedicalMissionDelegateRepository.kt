package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.model.MedicalMissionDelegate
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
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

/** Store result for delegate assign (#323): `inserted=false` marks an idempotent same-id retry. */
data class DelegateAssignResult(
    val delegate: MedicalMissionDelegate,
    val inserted: Boolean,
)

data class DelegateRevokeResult(
    val delegate: MedicalMissionDelegate,
    val changed: Boolean,
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

    fun findByBranchId(branchId: UUID): List<MedicalMissionDelegate> =
        transaction {
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.branchId eq branchId }
                .orderBy(
                    MedicalMissionDelegateTable.endedAt to SortOrder.DESC,
                    MedicalMissionDelegateTable.assignedAt to SortOrder.DESC,
                    MedicalMissionDelegateTable.id to SortOrder.ASC,
                ).map { it.toMedicalMissionDelegate() }
        }

    fun findActiveByTargetAndBranchInTransaction(
        targetUserId: UUID,
        branchId: UUID,
    ): MedicalMissionDelegate? =
        MedicalMissionDelegateTable
            .selectAll()
            .where {
                (MedicalMissionDelegateTable.targetUser eq targetUserId) and
                    (MedicalMissionDelegateTable.branchId eq branchId) and
                    (MedicalMissionDelegateTable.endedAt.isNull())
            }.singleOrNull()
            ?.toMedicalMissionDelegate()

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. Owns the delegate row only; the branch-scoped capability grant is
     * written by the command through the authorization seam (#538) in the same
     * transaction, so neither commits without the other.
     */
    fun assignInTransaction(
        delegateId: UUID,
        targetUserId: UUID,
        assignedBy: UUID,
        branchId: UUID,
    ): DelegateAssignResult {
        val inserted =
            MedicalMissionDelegateTable
                .insertIgnore {
                    it[MedicalMissionDelegateTable.id] = delegateId
                    it[MedicalMissionDelegateTable.targetUser] = targetUserId
                    it[MedicalMissionDelegateTable.assignedBy] = assignedBy
                    it[MedicalMissionDelegateTable.branchId] = branchId
                    it[MedicalMissionDelegateTable.assignedAt] = CurrentTimestampWithTimeZone
                }.insertedCount > 0

        if (!inserted) {
            val sameIdExists =
                MedicalMissionDelegateTable
                    .selectAll()
                    .where { MedicalMissionDelegateTable.id eq delegateId }
                    .empty()
                    .not()
            if (!sameIdExists) {
                throw ConflictException("User already has an active medical mission delegate at this branch")
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
     * transaction. Owns the delegate row only; the capability-window close goes
     * through the authorization seam (#538) in the same transaction.
     */
    fun revokeInTransaction(delegateId: UUID): DelegateRevokeResult {
        val changed =
            MedicalMissionDelegateTable
                .update({
                    (MedicalMissionDelegateTable.id eq delegateId) and
                        (MedicalMissionDelegateTable.endedAt.isNull())
                }) {
                    it[MedicalMissionDelegateTable.endedAt] = CurrentTimestampWithTimeZone
                } > 0

        val delegate =
            MedicalMissionDelegateTable
                .selectAll()
                .where { MedicalMissionDelegateTable.id eq delegateId }
                .single()
                .toMedicalMissionDelegate()
        return DelegateRevokeResult(delegate, changed)
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
