package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID

/**
 * Branch and workforce fixtures for map #533 (#552).
 *
 * Owns branch, assignment, branch-day and attendance rows. Capability writes
 * delegate to [IdentityFixtures] as an explicit cross-family composition.
 */
object BranchWorkforceFixtures {
    fun insertTestBranch(
        id: UUID,
        name: String = "Test Branch ${id.toString().take(8)}",
        branchType: BranchType = BranchType.CLINIC,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = branchType
            }
        }
    }

    /**
     * Inserts a [user_branch_assignment] row directly (bypasses
     * [com.companyb.companyapp.repository.UserBranchAssignmentRepository]).
     * NOTE: inside `insert {}` the lambda receiver is the TABLE, so unqualified
     * names that collide with table columns resolve to COLUMNS, not to enclosing
     * scope — function parameters and locals win, but object properties lose.
     * Always pass local values or explicitly-qualified references (this is why
     * the repository uses `params.*`).
     */
    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
    fun insertTestAssignment(
        id: UUID = TestFixtures.uuid(),
        userId: UUID,
        branchId: UUID,
        slot: Short,
        assignedBy: UUID,
        ended: Boolean = false,
    ): UUID {
        transaction {
            UserBranchAssignmentTable.insert {
                it[UserBranchAssignmentTable.id] = id
                it[UserBranchAssignmentTable.userId] = userId
                it[UserBranchAssignmentTable.branchId] = branchId
                it[UserBranchAssignmentTable.slot] = slot
                it[UserBranchAssignmentTable.assignedBy] = assignedBy
                if (ended) it[UserBranchAssignmentTable.endedAt] = CurrentTimestampWithTimeZone
            }
        }
        return id
    }

    fun createBranchDayForToday(branchId: UUID): UUID = createBranchDayForDate(branchId, TestFixtures.today)

    fun createBranchDayForDate(
        branchId: UUID,
        date: LocalDate,
    ): UUID =
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq date)
                }.single()[BranchDayTable.id]
        }

    /** Creates a REMITTED branch day for [date] (e.g. a past covered day). */
    fun createRemittedBranchDay(
        branchId: UUID,
        date: LocalDate,
    ): UUID =
        createBranchDayForDate(branchId, date).also { id ->
            transaction {
                BranchDayTable.update({ BranchDayTable.id eq id }) {
                    it[BranchDayTable.status] = DayStatus.REMITTED
                }
            }
        }

    /** Grants EDIT_PAST_DAY at [branchId] — the capability that permits writes on PAST/REMITTED days. */
    fun grantEditPastDay(
        userId: UUID,
        branchId: UUID,
        sourceId: UUID,
    ) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    fun insertTestAttendance(
        branchDayId: UUID,
        userId: UUID,
    ) {
        transaction {
            AttendanceTable.insertIgnore {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = TestFixtures.now
            }
        }
    }
}
