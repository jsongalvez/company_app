package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Work-day gate suite split from [CompensationServicePostgresTest] (RED repair
 * on 84079f7): each class stays under the LargeClass pin. No behavior change.
 */
class CompensationWorkDayGatePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var payingBranchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "comp-caller")
        IdentityFixtures.insertTestUser(targetUserId, "comp-target")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Compensation Branch")
        BranchWorkforceFixtures.createBranchDayForToday(branchId)
        payingBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantAssignCompensation(callerId, sourceId)
    }

    @Test
    fun `create compensation on REMITTED paying day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val compId = TestFixtures.uuid()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedDayId,
                payingBranchDayId = remittedDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Daily compensation",
                reason = "Coordinator correction",
            )

        assertNotNull(comp)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq compId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `create with REMITTED work day and OPEN paying day without EDIT_PAST_DAY is rejected`() {
        val remittedWorkDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        val compId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
        val stored = transaction { CompensationRepository.findByIdInTransaction(compId) }
        assertNull(stored)
    }

    @Test
    fun `create with REMITTED work day and OPEN paying day without reason is rejected`() {
        val remittedWorkDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        assertFailsWith<ValidationException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = remittedWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create with REMITTED work day and OPEN paying day with reason succeeds and flags audit entry`() {
        val remittedWorkDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val compId = TestFixtures.uuid()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
                reason = "Coordinator correction",
            )

        assertNotNull(comp)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq compId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `update with REMITTED work day and OPEN paying day without reason is rejected`() {
        val remittedWorkDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val compId = TestFixtures.uuid()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
                reason = "Initial correction",
            )

        assertFailsWith<ValidationException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = created.version,
            )
        }
    }

    @Test
    fun `update with REMITTED work day and OPEN paying day with reason succeeds and flags audit entry`() {
        val remittedWorkDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val compId = TestFixtures.uuid()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
                reason = "Initial correction",
            )

        val updated =
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = "Updated note",
                expectedVersion = created.version,
                reason = "Follow-up correction",
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq compId) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Follow-up correction", audit[AuditLogTable.reason])
    }
}
