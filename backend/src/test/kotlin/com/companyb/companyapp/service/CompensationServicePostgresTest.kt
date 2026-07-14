package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompensationServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private lateinit var workBranchDayId: UUID
    private lateinit var payingBranchDayId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(callerId, "comp-caller")
        insertUser(targetUserId, "comp-target")
        insertBranch(branchId, "Test Compensation Branch")
        workBranchDayId = createBranchDay(branchId)
        payingBranchDayId = createBranchDay(branchId)
        grantAssignCompensation(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
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
    fun `create without ASSIGN_COMPENSATION is forbidden`() {
        revokeCapabilities()

        assertFailsWith<ForbiddenResponse> {
            CompensationService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
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
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
        assertEquals("Updated note", updated.note)
    }

    @Test
    fun `update non-existent compensation returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = UUID.randomUUID(),
                amount = BigDecimal("2000.00"),
                note = null,
            )
        }
    }

    @Test
    fun `update without ASSIGN_COMPENSATION is forbidden`() {
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

        revokeCapabilities()

        assertFailsWith<ForbiddenResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
            )
        }
    }

    @Test
    fun `update with negative amount returns bad request`() {
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

        assertFailsWith<BadRequestResponse> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("-500.00"),
                note = null,
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
            CompensationTable.deleteAll()
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq callerId) or
                    (AppUserTable.id eq targetUserId)
            }
        }
    }
}
