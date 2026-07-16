package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
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
import kotlin.test.assertTrue

class CompensationServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private lateinit var workBranchDayId: UUID
    private lateinit var payingBranchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "comp-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "comp-target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Compensation Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        workBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        payingBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, targetUserId)
        trackOwned(CompensationTable, CompensationTable.userId, targetUserId)
    }

    @Test
    fun `create compensation succeeds with all fields`() {
        val compId = UUID.randomUUID()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Daily compensation",
            )

        assertNotNull(comp)
        assertEquals(compId, comp.id)
        assertEquals(workBranchDayId, comp.workBranchDayId)
        assertEquals(payingBranchDayId, comp.payingBranchDayId)
        assertEquals(targetUserId, comp.userId)
        assertEquals(0, BigDecimal("1500.00").compareTo(comp.amount))
        assertEquals(callerId, comp.assignedBy)
        assertEquals("Daily compensation", comp.note)
    }

    @Test
    fun `create compensation without note succeeds`() {
        val compId = UUID.randomUUID()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1000.00"),
                note = null,
            )

        assertNotNull(comp)
        assertNull(comp.note)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val compId = UUID.randomUUID()

        val first =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "First",
            )

        val second =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "First",
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create rejects duplicate user and paying day`() {
        val firstId = UUID.randomUUID()
        CompensationService.create(
            callerId = callerId,
            id = firstId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val secondId = UUID.randomUUID()
        assertFailsWith<ConflictResponse> {
            CompensationService.create(
                callerId = callerId,
                id = secondId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("2000.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val compId = UUID.randomUUID()
        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        trackOwned(CompensationTable, CompensationTable.assignedBy, callerId)
        assertNotNull(comp)
    }

    @Test
    fun `create with non-existent work branch day returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CompensationService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                workBranchDayId = UUID.randomUUID(),
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create with non-existent paying branch day returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CompensationService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                workBranchDayId = workBranchDayId,
                payingBranchDayId = UUID.randomUUID(),
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create with negative amount returns bad request`() {
        assertFailsWith<BadRequestResponse> {
            CompensationService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("-100.00"),
                note = null,
            )
        }
    }

    @Test
    fun `update compensation succeeds`() {
        val compId = UUID.randomUUID()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Original note",
            )

        val updated =
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = "Updated note",
                expectedVersion = created.version,
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
        assertEquals("Updated note", updated.note)
        assertEquals(created.version + 1, updated.version)
    }

    @Test
    fun `update with wrong version returns conflict`() {
        val compId = UUID.randomUUID()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        assertFailsWith<ConflictResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 999,
            )
        }
    }

    @Test
    fun `update non-existent compensation returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = UUID.randomUUID(),
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update without ASSIGN_COMPENSATION is allowed at service layer`() {
        val compId = UUID.randomUUID()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val updated =
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 1,
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
    }

    @Test
    fun `update with negative amount returns bad request`() {
        val compId = UUID.randomUUID()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        assertFailsWith<BadRequestResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("-500.00"),
                note = null,
                expectedVersion = created.version,
            )
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val compId = UUID.randomUUID()

        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CompensationTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `update writes audit log entry`() {
        val compId = UUID.randomUUID()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        CompensationService.update(
            callerId = callerId,
            compensationId = compId,
            amount = BigDecimal("2000.00"),
            note = "Updated",
            expectedVersion = created.version,
        )

        val updateAuditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertTrue(updateAuditCount > 0)
    }
}
