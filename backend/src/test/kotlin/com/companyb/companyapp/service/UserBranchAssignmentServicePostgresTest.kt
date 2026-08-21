package com.companyb.companyapp.service
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserBranchAssignmentServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val nonManagerId = TestFixtures.uuid()
    private val userAId = TestFixtures.uuid()
    private val userBId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(nonManagerId, "nonmgr")
        trackOwned(AppUserTable, AppUserTable.id, nonManagerId)
        DatabaseTestHelper.insertTestUser(userAId, "usera")
        trackOwned(AppUserTable, AppUserTable.id, userAId)
        DatabaseTestHelper.insertTestUser(userBId, "userb")
        trackOwned(AppUserTable, AppUserTable.id, userBId)
        DatabaseTestHelper.insertTestBranch(branchId, name = "Test Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, nonManagerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, userAId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, userBId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.branchId, branchId)
    }

    @Test
    fun `create persists assignment and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        val slot: Short = 3

        val result = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, slot)

        assertTrue(result.created)
        assertEquals(assignmentId, result.assignment.id)
        assertEquals(userAId, result.assignment.userId)
        assertEquals(branchId, result.assignment.branchId)
        assertEquals(slot, result.assignment.slot)
        assertEquals(callerId, result.assignment.assignedBy)
        assertNotNull(result.assignment.assignedAt)
        assertEquals(1L, auditEntryCount(assignmentId))
        assertEquals(callerId.toString(), auditChangedBy(assignmentId))
    }

    @Test
    fun `duplicate assignment id returns existing row without extra audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        val duplicate = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userBId, 5)

        assertTrue(duplicate.created.not())
        assertEquals(assignmentId, duplicate.assignment.id)
        assertEquals(userAId, duplicate.assignment.userId)
        assertEquals(1, duplicate.assignment.slot)
        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `same assignment retry returns existing row without extra audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        val retry = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertTrue(retry.created.not())
        assertEquals(assignmentId, retry.assignment.id)
        assertEquals(userAId, retry.assignment.userId)
        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `create with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, unknownBranchId, userAId, 1)
        }
    }

    @Test
    fun `create with non-existent user throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        val unknownUserId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, unknownUserId, 1)
        }
    }

    @Test
    fun `create when user already has active assignment at branch throws BadRequest`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val firstId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, firstId, branchId, userAId, 1)

        val secondId = TestFixtures.uuid()
        assertFailsWith<ValidationException> {
            UserBranchAssignmentService.create(callerId, secondId, branchId, userAId, 2)
        }
    }

    @Test
    fun `concurrent active assignment creation returns one success and one conflict`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val executor = Executors.newFixedThreadPool(CONCURRENT_ASSIGNMENTS)
        val ready = CountDownLatch(CONCURRENT_ASSIGNMENTS)
        val start = CountDownLatch(1)
        val assignmentIds = (1..CONCURRENT_ASSIGNMENTS).map { TestFixtures.uuid() }
        val futures =
            assignmentIds.map { assignmentId ->
                executor.submit<Result<Boolean>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)
                        true
                    }
                }
            }
        val results =
            try {
                ready.await()
                start.countDown()
                futures.map { it.get() }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            }

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() })
        val conflict = results.single { it.isFailure }.exceptionOrNull()
        assertTrue(conflict is ConflictException || conflict is ValidationException)
        assertEquals(1, activeAssignmentCount(userAId))
        val successfulId = assignmentIds[results.indexOfFirst { it.isSuccess }]
        val losingId = assignmentIds.single { it != successfulId }
        assertEquals(1L, auditEntryCount(successfulId))
        assertEquals(0L, auditEntryCount(losingId))
    }

    @Test
    fun `repository race classifies active key conflict without audit callback`() {
        val executor = Executors.newFixedThreadPool(CONCURRENT_ASSIGNMENTS)
        val ready = CountDownLatch(CONCURRENT_ASSIGNMENTS)
        val start = CountDownLatch(1)
        val auditCalls = AtomicInteger(0)
        val assignmentIds = (1..CONCURRENT_ASSIGNMENTS).map { TestFixtures.uuid() }
        val futures =
            assignmentIds.map { assignmentId ->
                executor.submit<Result<Boolean>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        UserBranchAssignmentRepository.create(
                            UserBranchAssignmentCreateParams(
                                id = assignmentId,
                                userId = userBId,
                                branchId = branchId,
                                slot = 1,
                                assignedBy = callerId,
                            ),
                            auditFn = { auditCalls.incrementAndGet() },
                        )
                    }
                }
            }
        val results =
            try {
                ready.await()
                start.countDown()
                futures.map { it.get() }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            }

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is ConflictException)
        assertEquals(1, auditCalls.get())
    }

    @Test
    fun `create without MANAGE_USERS is forbidden at service layer`() {
        val assignmentId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.create(nonManagerId, assignmentId, branchId, userAId, 1)
        }
    }

    @Test
    fun `remove sets endedAt and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.remove(callerId, branchId, userAId)

        assertNotNull(assignmentEndedAt(assignmentId))
        assertEquals(2L, auditEntryCount(assignmentId))
        val auditRow = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", auditRow.action)
    }

    @Test
    fun `remove without MANAGE_USERS is forbidden at service layer`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.remove(nonManagerId, branchId, userAId)
        }

        assertNull(assignmentEndedAt(assignmentId))
    }

    @Test
    fun `remove with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, unknownBranchId, userAId)
        }
    }

    @Test
    fun `remove with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, branchId, userAId)
        }
    }

    @Test
    fun `updateSlot by manager updates slot and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.updateSlot(callerId, branchId, userAId, 5)

        assertEquals(5, assignedSlot(assignmentId))
        val audit = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", audit.action)
        assertEquals("1", audit.oldSlot)
        assertEquals("5", audit.newSlot)
    }

    @Test
    fun `updateSlot self-update allowed without MANAGE_USERS`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, nonManagerId, 2)

        UserBranchAssignmentService.updateSlot(nonManagerId, branchId, nonManagerId, 7)

        assertEquals(7, assignedSlot(assignmentId))
    }

    @Test
    fun `updateSlot with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, userAId, 1)
        }
    }

    @Test
    fun `updateSlot by non-manager for other user throws Forbidden`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.updateSlot(nonManagerId, branchId, userAId, 3)
        }

        assertEquals(1, assignedSlot(assignmentId))
    }

    @Test
    fun `swapSlots swaps slots and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 2)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 8)

        UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)

        assertEquals(8, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
        assertEquals(2L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots without MANAGE_USERS is forbidden at service layer`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.swapSlots(nonManagerId, branchId, userAId, userBId)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
    }

    @Test
    fun `swapSlots self-service by participant is allowed without MANAGE_USERS`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        UserBranchAssignmentService.swapSlots(userAId, branchId, userAId, userBId)

        assertEquals(2, assignedSlot(idA))
        assertEquals(1, assignedSlot(idB))
    }

    @Test
    fun `swapSlots with the same user on both sides throws validation without audit rows`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<ValidationException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userAId)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(1L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots with missing assignment A throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)
        }
    }

    @Test
    fun `swapSlots with missing assignment B throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, userAId, userBId)
        }
    }

    @Test
    fun `findActiveByBranch returns active assignments sorted by slot`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 5)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(2, assignments.size)
        assertEquals(userAId, assignments[0].userId)
        assertEquals(1, assignments[0].slot)
        assertEquals(userBId, assignments[1].userId)
        assertEquals(5, assignments[1].slot)
    }

    @Test
    fun `findActiveByBranch excludes ended assignments`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)
        UserBranchAssignmentService.remove(callerId, branchId, userBId)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertEquals(1, assignments.size)
        assertEquals(userAId, assignments[0].userId)
    }

    @Test
    fun `findActiveByBranch without MANAGE_USERS is forbidden at service layer`() {
        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.findActiveByBranch(nonManagerId, branchId)
        }
    }

    @Test
    fun `findActiveByBranch with MANAGE_USERS returns empty list`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertTrue(assignments.isEmpty())
    }

    private fun assignedSlot(assignmentId: UUID): Short =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .single()[UserBranchAssignmentTable.slot]
        }

    private fun assignmentEndedAt(assignmentId: UUID): OffsetDateTime? =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where { UserBranchAssignmentTable.id eq assignmentId }
                .singleOrNull()
                ?.let { it[UserBranchAssignmentTable.endedAt] }
        }

    private fun auditEntryCount(assignmentId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "user_branch_assignment") and
                        (AuditLogTable.recordId eq assignmentId)
                }.count()
        }

    private fun auditChangedBy(assignmentId: UUID): String =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "user_branch_assignment") and
                        (AuditLogTable.recordId eq assignmentId)
                }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                .limit(1)
                .single()[AuditLogTable.changedBy]
                .toString()
        }

    private data class AssignmentAudit(
        val action: String,
        val oldSlot: String,
        val newSlot: String,
    )

    private fun latestAuditEntry(assignmentId: UUID): AssignmentAudit =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "user_branch_assignment") and
                            (AuditLogTable.recordId eq assignmentId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()

            AssignmentAudit(
                action = row[AuditLogTable.action].name,
                oldSlot = DatabaseTestHelper.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "slot"),
                newSlot = DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "slot"),
            )
        }

    private fun activeAssignmentCount(userId: UUID): Long =
        transaction {
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.branchId eq branchId) and
                        UserBranchAssignmentTable.endedAt.isNull()
                }.count()
        }

    private companion object {
        const val CONCURRENT_ASSIGNMENTS = 2
        const val EXECUTOR_TERMINATION_SECONDS = 5L
    }
}
