package com.companyb.companyapp.service
import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
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
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
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

@Suppress("LargeClass")
class UserBranchAssignmentServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val nonManagerId = TestFixtures.uuid()
    private val userAId = TestFixtures.uuid()
    private val userBId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        DatabaseTestHelper.insertTestUser(nonManagerId, "nonmgr")
        DatabaseTestHelper.insertTestUser(userAId, "usera")
        DatabaseTestHelper.insertTestUser(userBId, "userb")
        DatabaseTestHelper.insertTestBranch(branchId, name = "Test Branch")
    }

    @Test
    fun `create persists assignment and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val otherBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(otherBranchId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
    fun `create with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, unknownBranchId, userAId, 1)
        }
    }

    @Test
    fun `create with non-existent user throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        val unknownUserId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.create(callerId, assignmentId, branchId, unknownUserId, 1)
        }
    }

    @Test
    fun `create when user already has active assignment at branch throws BadRequest`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is ConflictException)
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

    @Test
    fun `remove sets endedAt and writes audit row`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.remove(callerId, branchId, assignmentId)

        assertNotNull(assignmentEndedAt(assignmentId))
        assertEquals(2L, auditEntryCount(assignmentId))
        val auditRow = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", auditRow.action)
    }

    @Test
    fun `stale remove identity cannot end replacement assignment`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 2)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        }

        assertNull(assignmentEndedAt(replacementAssignmentId))
        assertEquals(2, assignedSlot(replacementAssignmentId))
    }

    @Test
    fun `remove without MANAGE_USERS is forbidden at service layer`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.remove(nonManagerId, branchId, assignmentId)
        }

        assertNull(assignmentEndedAt(assignmentId))
    }

    @Test
    fun `remove with non-existent branch throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, unknownBranchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `remove with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.remove(callerId, branchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `updateSlot by manager updates slot and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        UserBranchAssignmentService.updateSlot(callerId, branchId, assignmentId, 5)

        assertEquals(5, assignedSlot(assignmentId))
        val audit = latestAuditEntry(assignmentId)
        assertEquals("UPDATE", audit.action)
        assertEquals("1", audit.oldSlot)
        assertEquals("5", audit.newSlot)
    }

    @Test
    fun `updateSlot self-update allowed without MANAGE_USERS`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, nonManagerId, 2)

        UserBranchAssignmentService.updateSlot(nonManagerId, branchId, assignmentId, 7)

        assertEquals(7, assignedSlot(assignmentId))
    }

    @Test
    fun `updateSlot with no active assignment throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, TestFixtures.uuid(), 1)
        }
    }

    @Test
    fun `updateSlot by non-manager for other user throws Forbidden`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, assignmentId, branchId, userAId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.updateSlot(nonManagerId, branchId, assignmentId, 3)
        }

        assertEquals(1, assignedSlot(assignmentId))
    }

    @Test
    fun `stale slot update identity cannot edit replacement assignment`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 2)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.updateSlot(callerId, branchId, staleAssignmentId, 9)
        }

        assertEquals(2, assignedSlot(replacementAssignmentId))
    }

    @Test
    fun `swapSlots swaps slots and writes audit`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 2)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 8)

        UserBranchAssignmentService.swapSlots(callerId, branchId, idA, idB)

        assertEquals(8, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
        assertEquals(2L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots without MANAGE_USERS is forbidden at service layer`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.swapSlots(nonManagerId, branchId, idA, idB)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(2, assignedSlot(idB))
    }

    @Test
    fun `swapSlots self-service by participant is allowed without MANAGE_USERS`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = TestFixtures.uuid()
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)

        UserBranchAssignmentService.swapSlots(userAId, branchId, idA, idB)

        assertEquals(2, assignedSlot(idA))
        assertEquals(1, assignedSlot(idB))
    }

    @Test
    fun `swapSlots with the same user on both sides throws validation without audit rows`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<ValidationException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idA, idA)
        }

        assertEquals(1, assignedSlot(idA))
        assertEquals(1L, auditEntryCount(idA))
    }

    @Test
    fun `swapSlots with missing assignment A throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idB, TestFixtures.uuid())
        }
    }

    @Test
    fun `swapSlots with missing assignment B throws NotFound`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val idA = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idA, branchId, userAId, 1)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, idA, TestFixtures.uuid())
        }
    }

    @Test
    fun `stale swap identity cannot edit replacement assignment`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val staleAssignmentId = TestFixtures.uuid()
        val replacementAssignmentId = TestFixtures.uuid()
        val otherAssignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, staleAssignmentId, branchId, userAId, 1)
        UserBranchAssignmentService.remove(callerId, branchId, staleAssignmentId)
        UserBranchAssignmentService.create(callerId, replacementAssignmentId, branchId, userAId, 3)
        UserBranchAssignmentService.create(callerId, otherAssignmentId, branchId, userBId, 7)

        assertFailsWith<NotFoundException> {
            UserBranchAssignmentService.swapSlots(callerId, branchId, staleAssignmentId, otherAssignmentId)
        }

        assertEquals(3, assignedSlot(replacementAssignmentId))
        assertEquals(7, assignedSlot(otherAssignmentId))
    }

    @Test
    fun `findActiveByBranch returns active assignments sorted by slot`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
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
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        val idB = TestFixtures.uuid()
        UserBranchAssignmentService.create(callerId, idB, branchId, userBId, 2)
        UserBranchAssignmentService.remove(callerId, branchId, idB)

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

        val assignments = UserBranchAssignmentService.findActiveByBranch(callerId, branchId)

        assertTrue(assignments.isEmpty())
    }

    // --- #366 requested-practitioner directory ---

    @Test
    fun `listActiveMembers returns active member names in slot order`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, callerId, 3)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 5)

        val members = UserBranchAssignmentService.listActiveMembers(userAId, branchId)

        assertEquals(
            listOf("Test usera", "Test caller", "Test userb"),
            members.map { it.displayName },
        )
        assertEquals(3, members.map { it.id }.distinct().size)
    }

    @Test
    fun `listActiveMembers without membership is forbidden at service layer`() {
        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)
        }
    }

    @Test
    fun `listActiveMembers rejects a member of a different branch`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val otherBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(otherBranchId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), otherBranchId, userBId, 1)

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(userBId, branchId)
        }
    }

    @Test
    fun `listActiveMembers excludes deactivated users`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userBId, 2)
        transaction {
            AppUserTable.update({ AppUserTable.id eq userBId }) {
                it[status] = UserStatus.INACTIVE
            }
        }

        val members = UserBranchAssignmentService.listActiveMembers(userAId, branchId)

        assertEquals(listOf(userAId), members.map { it.id })
    }

    @Test
    fun `listActiveMembers allows a BRANCH_DAY edit grant for todays branch day`() {
        // #402 — relief duty: the grant shape the session-create gate accepts must also
        // load the practitioner directory those flows pick from.
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        val todayBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.grantCapability(
            userId = nonManagerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = todayBranchDayId,
            sourceId = sourceId,
        )

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(listOf("Test usera"), members.map { it.displayName })
    }

    @Test
    fun `listActiveMembers allows a BRANCH-scoped edit grant when a day row exists`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.grantCapability(
            userId = nonManagerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(listOf("Test usera"), members.map { it.displayName })
    }

    @Test
    fun `listActiveMembers allows a BRANCH-scoped edit grant with no day row yet`() {
        // Session-create fallback parity (#402 review): the plain BRANCH leg governs when
        // findToday resolves nothing — no branch-day existence oracle.
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        UserBranchAssignmentService.create(callerId, TestFixtures.uuid(), branchId, userAId, 1)
        DatabaseTestHelper.grantCapability(
            userId = nonManagerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )

        val members = UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)

        assertEquals(listOf("Test usera"), members.map { it.displayName })
    }

    @Test
    fun `listActiveMembers rejects a day grant scoped to another days row`() {
        // Today's row exists but the grant references yesterday's — the mismatch path
        // (not the missing-day fallback) must reject.
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        val todayBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val yesterdayBranchDayId =
            DatabaseTestHelper.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        check(todayBranchDayId != yesterdayBranchDayId)
        DatabaseTestHelper.grantCapability(
            userId = nonManagerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = yesterdayBranchDayId,
            sourceId = sourceId,
        )

        assertFailsWith<ForbiddenException> {
            UserBranchAssignmentService.listActiveMembers(nonManagerId, branchId)
        }
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
