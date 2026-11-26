package com.companyb.companyapp.workforce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import com.companyb.companyapp.workforce.UserBranchAssignmentTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Creating a branch assignment: replays, id conflicts, races and who may create one. */
class UserBranchAssignmentServicePostgresTest : UserBranchAssignmentServicePostgresBase() {
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

    @Test
    fun `create persists assignment and writes audit row`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
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
    fun `same id for another user conflicts without mutation or audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ConflictException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, userBId, 5)
        }

        assertEquals(1L, auditEntryCount(assignmentId))
        assertEquals(0L, activeAssignmentCount(userBId))
    }

    @Test
    fun `same assignment retry returns existing row without extra audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        val retry = UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertTrue(retry.created.not())
        assertEquals(assignmentId, retry.assignment.id)
        assertEquals(userAId, retry.assignment.userId)
        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `same id for another branch conflicts without mutation or audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ConflictException> {
            UserBranchAssignmentService.create(callerId, assignmentId, otherBranchId, userAId, 1)
        }

        assertEquals(1L, auditEntryCount(assignmentId))
        assertNull(UserBranchAssignmentRepository.findActiveByBranchAndUser(otherBranchId, userAId))
    }

    @Test
    fun `same id reuse when target key occupied conflicts`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)
        val otherId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, otherId, branchId, userBId, 2)

        assertFailsWith<ConflictException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, userBId, 2)
        }

        assertEquals(1L, auditEntryCount(assignmentId))
        assertEquals(1L, auditEntryCount(otherId))
        assertEquals(otherId, UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userBId)?.id)
    }

    @Test
    fun `same id with different slot conflicts without mutation or audit`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ConflictException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 2)
        }

        assertEquals(1, assignedSlot(assignmentId))
        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `concurrent same id for different owners classifies deterministically`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val sharedId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(CONCURRENT_ASSIGNMENTS)
        val ready = CountDownLatch(CONCURRENT_ASSIGNMENTS)
        val start = CountDownLatch(1)
        val targets = listOf(userAId, userBId)
        val futures =
            targets.map { target ->
                executor.submit<Result<UserBranchAssignmentService.CreateResult>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        UserBranchAssignmentService.create(callerId, sharedId, branchId, target, 1)
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

        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is ConflictException)
        val winner = results.single { it.isSuccess }.getOrThrow()
        assertEquals(sharedId, winner.assignment.id)
        assertEquals(1L, auditEntryCount(sharedId))
        val loserId = targets.single { it != winner.assignment.userId }
        assertEquals(0L, activeAssignmentCount(loserId))
    }

    @Test
    fun `repository same id with different owner throws conflict`() {
        val assignmentId = TestFixtures.uuid()
        transaction {
            val result =
                UserBranchAssignmentRepository.createInTransaction(
                    UserBranchAssignmentCreateParams(
                        id = assignmentId,
                        userId = userAId,
                        branchId = branchId,
                        slot = 1,
                        assignedBy = callerId,
                    ),
                )
            if (result.created) {
                UserBranchAssignmentAudit.inserted(
                    AuditContext(callerId, branchId),
                    result.assignment,
                )
            }
        }

        assertFailsWith<ConflictException> {
            transaction {
                UserBranchAssignmentRepository.createInTransaction(
                    UserBranchAssignmentCreateParams(
                        id = assignmentId,
                        userId = userBId,
                        branchId = branchId,
                        slot = 1,
                        assignedBy = callerId,
                    ),
                )
            }
        }

        assertEquals(1L, auditEntryCount(assignmentId))
    }

    @Test
    fun `a medical mission and a provincial tour take no staff, only helpers`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        listOf(BranchType.MEDICAL_MISSION, BranchType.PROVINCIAL_TOUR).forEach { type ->
            val away = TestFixtures.uuid()
            BranchWorkforceFixtures.insertTestBranch(away, branchType = type)

            val refused =
                assertFailsWith<ValidationException>(type.name) {
                    UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), away, userAId, 1)
                }

            assertEquals("Add them as a helper instead.", refused.message)
        }
    }

    @Test
    fun `create with non-existent branch throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, unknownBranchId, userAId, 1)
        }
    }

    @Test
    fun `create with non-existent user throws NotFound`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val assignmentId = TestFixtures.uuid()
        val unknownUserId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, unknownUserId, 1)
        }
    }

    @Test
    fun `create when user already has active assignment at branch throws BadRequest`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
        val firstId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, firstId, branchId, userAId, 1)

        val secondId = TestFixtures.uuid()
        assertFailsWith<ValidationException> {
            UserBranchAssignmentService.create(callerId, secondId, branchId, userAId, 2)
        }
    }

    @Test
    fun `concurrent active assignment creation returns one success and one conflict`() {
        IdentityFixtures.hold(callerId, Role.MANAGER)
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

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() }, "one winner, results: $results")
        val conflict = results.single { it.isFailure }.exceptionOrNull()
        assertTrue(conflict is ValidationException, "loser: $conflict")
        assertEquals(1, activeAssignmentCount(userAId))
        val successfulId = assignmentIds[results.indexOfFirst { it.isSuccess }]
        val losingId = assignmentIds.single { it != successfulId }
        assertEquals(1L, auditEntryCount(successfulId))
        assertEquals(0L, auditEntryCount(losingId))
    }

    @Test
    fun `repository race classifies active key conflict and audits exactly the winner`() {
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
                        transaction {
                            val result =
                                UserBranchAssignmentRepository.createInTransaction(
                                    UserBranchAssignmentCreateParams(
                                        id = assignmentId,
                                        userId = userBId,
                                        branchId = branchId,
                                        slot = 1,
                                        assignedBy = callerId,
                                    ),
                                )
                            if (result.created) {
                                UserBranchAssignmentAudit.inserted(
                                    AuditContext(callerId, branchId),
                                    result.assignment,
                                )
                            }
                            result.created
                        }
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

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() }, "one winner, results: $results")
        val loser = results.single { it.isFailure }.exceptionOrNull()
        assertTrue(loser is ValidationException, "overlapping or not, the loser gets a 400: $loser")
        val winningId = assignmentIds[results.indexOfFirst { it.isSuccess }]
        val losingId = assignmentIds.single { it != winningId }
        assertEquals(1L, auditEntryCount(winningId))
        assertEquals(0L, auditEntryCount(losingId))
    }

    @Test
    fun `create without MANAGE_USERS is forbidden at service layer`() {
        val assignmentId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.create(nonManagerId, assignmentId, branchId, userAId, 1)
        }
    }
}
