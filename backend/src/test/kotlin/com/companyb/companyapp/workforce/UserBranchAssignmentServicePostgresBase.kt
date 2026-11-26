package com.companyb.companyapp.workforce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test

/** Users, branch and audit helpers shared by the branch assignment service suites. */
abstract class UserBranchAssignmentServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val nonManagerId = TestFixtures.uuid()
    protected val userAId = TestFixtures.uuid()
    protected val userBId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "caller")
        IdentityFixtures.insertTestUser(nonManagerId, "nonmgr")
        IdentityFixtures.insertTestUser(userAId, "usera")
        IdentityFixtures.insertTestUser(userBId, "userb")
        BranchWorkforceFixtures.insertTestBranch(branchId, name = "Test Branch")
    }

    protected fun assignedSlot(assignmentId: UUID): Short =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .single()[UserBranchAssignmentTable.slot]
        }

    protected fun auditEntryCount(assignmentId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "user_branch_assignment") and
                        (AuditLogTable.recordId eq assignmentId)
                }.count()
        }

    protected companion object {
        const val CONCURRENT_ASSIGNMENTS = 2
        const val EXECUTOR_TERMINATION_SECONDS = 5L
    }
}
