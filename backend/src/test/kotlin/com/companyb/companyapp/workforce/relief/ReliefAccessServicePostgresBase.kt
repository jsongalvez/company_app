package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * A relief ask names no target user and is scoped to one branch day (today or later); any active outsider may raise
 * it, any active branch member grants, denies or cancels it, and every retraction locks once the requester clocks
 * in. Ids, seed rows and helpers shared by the relief access suites.
 */
abstract class ReliefAccessServicePostgresBase : BasePostgresTest() {
    protected val reliefUserId = TestFixtures.uuid()
    protected val memberId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val branchName = "ReliefTest-${branchId.toString().take(8)}"
    protected val branchDayId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(reliefUserId, "relief")
        IdentityFixtures.insertTestUser(memberId, "member")
        BranchWorkforceFixtures.insertTestBranch(branchId, branchName)
        insertBranchDay(branchDayId, branchId)
        // The member's home assignment — grant/deny/cancel authority (#357).
        BranchWorkforceFixtures.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        // Member-authored grants/denies/cancels write audit rows too (#357 membership authority).
        // #358 — broadcast writes land in the notification table; every recipient's row
        // carries this branch id, so one column key tracks them all.
    }

    /** Track a service-created request row and its (possibly fresh) branch day for teardown. */
    protected fun trackRequest(requestId: UUID) {
        ReliefAccessRepository.findById(requestId)?.branchDayId
    }

    protected fun insertBranchDay(
        id: UUID,
        branchId: UUID,
        status: DayStatus = DayStatus.OPEN,
        date: LocalDate = TestFixtures.today,
    ) {
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
                it[BranchDayTable.status] = status
            }
        }
    }

    protected fun auditReliefEntryCount(requestId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq GrantReliefAccessTable.tableName) and
                        (AuditLogTable.recordId eq requestId)
                }.count()
        }

    protected companion object {
        const val FUTURE_DAYS = 2L
    }
}
