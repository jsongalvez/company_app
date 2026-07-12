package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object ReliefAccessRepository {
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
