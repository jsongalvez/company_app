package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.workforce.AttendanceTable
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Persistence for the Me surface: every `*Table` join and query lives behind this
 * internal store; MeService keeps only the active-user policy, the operational-day question,
 * and response shaping. Runs on the caller's (service-owned) transaction.
 */
internal object MeRepository {
    data class MeUser(
        val id: UUID,
        val username: String,
        val displayName: String,
        val email: String,
        val status: UserStatus,
        val createdAt: OffsetDateTime,
    )

    data class MeBranchRow(
        val branchId: UUID,
        val branchName: String,
        val branchType: BranchType,
        /** The user has an active branch assignment here. */
        val assigned: Boolean,
        /** The user has an active clock-in at this branch today (operational day). */
        val clockedInHereToday: Boolean,
        /** the active assignment's slot; null for relief rows (no assignment). */
        val slot: Short?,
        /** The active assignment id; null for relief rows. */
        val assignmentId: UUID?,
    )

    fun findUser(userId: UUID): MeUser? =
        AppUserTable
            .selectAll()
            .where { AppUserTable.id eq userId }
            .singleOrNull()
            ?.let { row ->
                MeUser(
                    id = row[AppUserTable.id],
                    username = row[AppUserTable.username],
                    displayName = row[AppUserTable.displayName],
                    email = row[AppUserTable.email],
                    status = row[AppUserTable.status],
                    createdAt = row[AppUserTable.createdAt],
                )
            }

    /** True when another account already uses [email] (emails are unique). */
    fun emailTakenByOther(
        userId: UUID,
        email: String,
    ): Boolean =
        AppUserTable
            .select(AppUserTable.id)
            .where { (AppUserTable.email eq email) and (AppUserTable.id neq userId) }
            .any()

    /** Write half of the self-service profile edit; runs on the service's transaction. */
    fun updateProfileInTransaction(
        userId: UUID,
        displayName: String,
        email: String,
    ) {
        AppUserTable.update({ AppUserTable.id eq userId }) {
            it[AppUserTable.displayName] = displayName
            it[AppUserTable.email] = email
        }
    }

    /** The caller's current password hash, for the change-password check. */
    fun passwordHash(userId: UUID): String? =
        AppUserTable
            .select(AppUserTable.passwordHash)
            .where { AppUserTable.id eq userId }
            .singleOrNull()
            ?.get(AppUserTable.passwordHash)

    fun findBranchRows(
        userId: UUID,
        operationalDay: LocalDate,
    ): List<MeBranchRow> {
        // the active assignments carry the Branch Slot per branch.
        val assignmentsByBranchId =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.endedAt.isNull())
                }.associate {
                    it[UserBranchAssignmentTable.branchId] to
                        (it[UserBranchAssignmentTable.id] to it[UserBranchAssignmentTable.slot])
                }
        val assignedBranchIds = assignmentsByBranchId.keys
        val clockedInTodayBranchIds =
            AttendanceTable
                .innerJoin(BranchDayTable, { AttendanceTable.branchDayId }, { BranchDayTable.id })
                .selectAll()
                .where {
                    (AttendanceTable.userId eq userId) and
                        (AttendanceTable.clockOut.isNull()) and
                        (BranchDayTable.date eq operationalDay)
                }.map { it[BranchDayTable.branchId] }
                .toSet()

        // Relief granted today has a member's rights there (owed #23), so the branch is theirs to pick
        // before they clock in.
        val grantedDayIds = CapabilityService.findDayWindow(userId)
        val reliefTodayBranchIds =
            if (grantedDayIds.isEmpty()) {
                emptySet()
            } else {
                BranchDayTable
                    .select(BranchDayTable.branchId)
                    .where { (BranchDayTable.id inList grantedDayIds) and (BranchDayTable.date eq operationalDay) }
                    .map { it[BranchDayTable.branchId] }
                    .toSet()
            }

        val branchIds = assignedBranchIds + clockedInTodayBranchIds + reliefTodayBranchIds
        if (branchIds.isEmpty()) return emptyList()

        return BranchTable
            .selectAll()
            .where { BranchTable.id inList branchIds }
            .orderBy(BranchTable.name to SortOrder.ASC, BranchTable.id to SortOrder.ASC)
            .map { row ->
                val branchId = row[BranchTable.id]
                MeBranchRow(
                    branchId = branchId,
                    branchName = row[BranchTable.name],
                    branchType = row[BranchTable.branchType],
                    assigned = branchId in assignedBranchIds,
                    clockedInHereToday = branchId in clockedInTodayBranchIds,
                    assignmentId = assignmentsByBranchId[branchId]?.first,
                    slot = assignmentsByBranchId[branchId]?.second,
                )
            }
    }
}
