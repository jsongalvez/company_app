package com.companyb.companyapp.workforce

import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** #366 — one active branch member for the requested-practitioner directory read. */
data class BranchMemberRow(
    val id: UUID,
    val displayName: String,
)

/**
 * #366 — the requested-practitioner directory reads: ACTIVE members (id + display name)
 * of one branch. Joins the user row and filters on status so deactivated users never
 * surface — the assignment query alone filters [UserBranchAssignmentTable.endedAt] only.
 */
internal object BranchMemberRepository {
    fun findActiveMemberNames(branchId: UUID): List<BranchMemberRow> =
        transaction {
            activeMembersQuery(branchId)
                .orderBy(
                    UserBranchAssignmentTable.slot to SortOrder.ASC,
                    AppUserTable.displayName to SortOrder.ASC,
                ).map { row ->
                    BranchMemberRow(
                        id = row[AppUserTable.id],
                        displayName = row[AppUserTable.displayName],
                    )
                }
        }

    /** Create-path revalidation: an ACTIVE member currently assigned to the branch. */
    fun hasActiveMember(
        branchId: UUID,
        userId: UUID,
    ): Boolean =
        transaction {
            UserBranchAssignmentTable
                .innerJoin(AppUserTable, { UserBranchAssignmentTable.userId }, { AppUserTable.id })
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.branchId eq branchId) and
                        (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.endedAt.isNull()) and
                        (AppUserTable.status eq UserStatus.ACTIVE)
                }.empty()
                .not()
        }

    /** Active member ids only (no display names) — notification audiences (#409). */
    fun findActiveMemberIds(branchId: UUID): List<UUID> =
        transaction {
            activeMembersQuery(branchId).map { row -> row[AppUserTable.id] }
        }

    private fun activeMembersQuery(branchId: UUID) =
        UserBranchAssignmentTable
            .innerJoin(AppUserTable, { UserBranchAssignmentTable.userId }, { AppUserTable.id })
            .selectAll()
            .where {
                (UserBranchAssignmentTable.branchId eq branchId) and
                    (UserBranchAssignmentTable.endedAt.isNull()) and
                    (AppUserTable.status eq UserStatus.ACTIVE)
            }
}
