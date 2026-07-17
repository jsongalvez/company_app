package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
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
import kotlin.test.assertTrue

class AllowanceServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "allowance-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "allowance-target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Allowance Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create allowance succeeds`() {
        val allowanceId = UUID.randomUUID()

        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        assertNotNull(allowance)
        assertEquals(allowanceId, allowance.id)
        assertEquals(branchDayId, allowance.branchDayId)
        assertEquals(targetUserId, allowance.userId)
        assertEquals(0, BigDecimal("500.00").compareTo(allowance.amount))
        assertEquals(callerId, allowance.assignedBy)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val allowanceId = UUID.randomUUID()

        val first =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        val second =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val allowanceId = UUID.randomUUID()
        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)
        assertNotNull(allowance)
        assertEquals(targetUserId, allowance.userId)
    }

    @Test
    fun `create with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            AllowanceService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = UUID.randomUUID(),
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `findByBranchDayId returns allowances for branch day`() {
        val allowanceId1 = UUID.randomUUID()
        val allowanceId2 = UUID.randomUUID()

        AllowanceService.create(
            callerId = callerId,
            id = allowanceId1,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )
        AllowanceService.create(
            callerId = callerId,
            id = allowanceId2,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("300.00"),
        )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId1)
        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId2)

        val results = AllowanceService.findByBranchDayId(branchDayId)

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == allowanceId1 })
        assertTrue(results.any { it.id == allowanceId2 })
    }

    @Test
    fun `findByBranchDayId returns empty list for non-existent branch day`() {
        val results = AllowanceService.findByBranchDayId(UUID.randomUUID())
        assertEquals(0, results.size)
    }

    @Test
    fun `findByBranchDayId without capability is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val results = AllowanceService.findByBranchDayId(branchDayId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `create writes audit log entry`() {
        val allowanceId = UUID.randomUUID()

        AllowanceService.create(
            callerId = callerId,
            id = allowanceId,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq AllowanceTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }
}
