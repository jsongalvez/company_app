package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AllowanceServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(callerId, "allowance-caller")
        insertUser(targetUserId, "allowance-target")
        insertBranch(branchId, "Test Allowance Branch")
        branchDayId = createBranchDay(branchId)
        grantAssignCompensation(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
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

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is forbidden`() {
        revokeCapabilities()

        assertFailsWith<ForbiddenResponse> {
            AllowanceService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `create with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundResponse> {
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
    fun `create with negative amount returns bad request`() {
        assertFailsWith<BadRequestResponse> {
            AllowanceService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("-100.00"),
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

        val results = AllowanceService.findByBranchDayId(callerId, branchDayId)

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == allowanceId1 })
        assertTrue(results.any { it.id == allowanceId2 })
    }

    @Test
    fun `findByBranchDayId returns empty list for non-existent branch day`() {
        val results = AllowanceService.findByBranchDayId(callerId, UUID.randomUUID())
        assertEquals(0, results.size)
    }

    @Test
    fun `findByBranchDayId without capability is forbidden`() {
        revokeCapabilities()

        assertFailsWith<ForbiddenResponse> {
            AllowanceService.findByBranchDayId(callerId, branchDayId)
        }
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

    private fun insertUser(
        userId: UUID,
        username: String,
    ) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "$username-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Test User $username",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
        }
    }

    private fun createBranchDay(branchId: UUID): UUID =
        transaction {
            val today = LocalDate.now(BranchDayService.manilaZone)
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = today
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq today)
                }.single()[BranchDayTable.id]
        }

    private fun grantAssignCompensation(userId: UUID) {
        DatabaseTestHelper.grantAssignCompensation(userId, sourceId)
    }

    private fun revokeCapabilities() {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
        }
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.changedBy eq targetUserId)
            }
            UserCapabilityTable.deleteWhere {
                (UserCapabilityTable.userId eq callerId) or
                    (UserCapabilityTable.userId eq targetUserId)
            }
            AllowanceTable.deleteAll()
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq callerId) or
                    (AppUserTable.id eq targetUserId)
            }
        }
    }
}
