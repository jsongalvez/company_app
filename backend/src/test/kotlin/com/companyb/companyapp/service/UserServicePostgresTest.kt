package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    override fun initTestData() {
        DenyList.clear()
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
    }

    @Test
    fun `deactivate persists inactive status, writes audit log, and rejects existing token`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val targetToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)

        assertEquals(UserStatus.INACTIVE, userStatus(targetUserId))
        assertTrue(DenyList.isDenied(targetUserId, Instant.EPOCH), "pre-deny token must stay denied")
        assertNull(JwtService.verifyToken(targetToken))

        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals(callerId.toString(), auditEntry.changedBy)
        assertEquals("ACTIVE", auditEntry.oldStatus)
        assertEquals("INACTIVE", auditEntry.newStatus)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `deactivate without MANAGE_USERS is allowed at service layer`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)

        assertEquals(UserStatus.INACTIVE, userStatus(targetUserId))
        assertEquals(1L, auditEntryCount(targetUserId))
    }

    @Test
    fun `deactivate self throws validation error`() {
        val error = assertFailsWith<ValidationException> { UserService.deactivate(callerId, callerId) }

        assertEquals("Cannot deactivate yourself", error.message)
        assertEquals(UserStatus.ACTIVE, userStatus(callerId))
        assertEquals(0L, auditEntryCount(callerId))
    }

    @Test
    fun `deactivate sets deactivatedAt timestamp`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)

        assertNotNull(deactivatedAt(targetUserId))
    }

    @Test
    fun `deactivating an already-inactive user is a no-op without audit row or timestamp reset`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)
        val firstStamp = deactivatedAt(targetUserId)
        assertNotNull(firstStamp)

        UserService.deactivate(callerId, targetUserId)

        assertEquals(1L, auditEntryCount(targetUserId))
        assertEquals(firstStamp, deactivatedAt(targetUserId))
    }

    @Test
    fun `reactivate flips status back to ACTIVE and clears deactivatedAt with audit`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)

        UserService.reactivate(callerId, targetUserId)

        assertEquals(UserStatus.ACTIVE, userStatus(targetUserId))
        assertNull(deactivatedAt(targetUserId))
        assertTrue(
            DenyList.isDenied(targetUserId, Instant.EPOCH),
            "old tokens stay dead — DenyList is not cleared by reactivate",
        )
        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals("INACTIVE", auditEntry.oldStatus)
        assertEquals("ACTIVE", auditEntry.newStatus)
        assertEquals(2L, auditEntryCount(targetUserId))
    }

    @Test
    fun `reactivated user can authenticate with a fresh token while the pre-deactivation token stays dead`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        val preDeactivationToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)
        assertNull(
            JwtService.verifyToken(preDeactivationToken),
            "pre-deactivation token must be dead after deactivation",
        )

        UserService.reactivate(callerId, targetUserId)
        assertNull(
            JwtService.verifyToken(preDeactivationToken),
            "pre-deactivation token must stay dead after reactivation",
        )

        // JWT iat is second-precision: a token generated in the same second as the deny
        // is indistinguishable from a pre-deny token and stays denied (DenyList KDoc).
        waitForNextSecond()
        val freshToken = JwtService.generateToken(targetUserId.toString())
        assertEquals(
            targetUserId.toString(),
            JwtService.verifyToken(freshToken),
            "fresh token issued after reactivation must verify",
        )
    }

    private fun waitForNextSecond() {
        val boundary = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)
        while (Instant.now().isBefore(boundary)) {
            Thread.sleep(10)
        }
    }

    @Test
    fun `reactivate of already-active user is a no-op without audit row`() {
        UserService.reactivate(callerId, targetUserId)

        assertEquals(UserStatus.ACTIVE, userStatus(targetUserId))
        assertEquals(0L, auditEntryCount(targetUserId))
    }

    @Test
    fun `reactivate of missing user throws not found`() {
        assertFailsWith<NotFoundException> { UserService.reactivate(callerId, UUID.randomUUID()) }
    }

    @Test
    fun `list returns users with active assignments only, ordered by displayName then username`() {
        val branchA = UUID.randomUUID()
        val branchB = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(branchA, "Branch A")
        DatabaseTestHelper.insertTestBranch(branchB, "Branch B")
        trackOwned(BranchTable, BranchTable.id, branchA)
        trackOwned(BranchTable, BranchTable.id, branchB)

        val assignment1 =
            DatabaseTestHelper.insertTestAssignment(
                userId = targetUserId,
                branchId = branchA,
                slot = 2,
                assignedBy = callerId,
            )
        val assignment2 =
            DatabaseTestHelper.insertTestAssignment(
                userId = targetUserId,
                branchId = branchB,
                slot = 1,
                assignedBy = callerId,
            )
        val endedAssignment =
            DatabaseTestHelper.insertTestAssignment(
                userId = callerId,
                branchId = branchA,
                slot = 9,
                assignedBy = callerId,
                ended = true,
            )
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, assignment1)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, assignment2)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.id, endedAssignment)

        val users = UserService.listUsers()

        assertEquals(2, users.size)
        assertEquals("Test caller", users[0].displayName)
        assertEquals("Test target", users[1].displayName)
        assertEquals(emptyList(), users[0].assignments, "ended assignments must be excluded")

        val target = users[1]
        assertEquals("ACTIVE", target.status)
        assertNull(target.deactivatedAt)
        assertEquals(listOf(branchB.toString(), branchA.toString()), target.assignments.map { it.branchId })
        assertEquals(listOf("Branch B", "Branch A"), target.assignments.map { it.branchName })
        assertEquals(listOf<Short>(1, 2), target.assignments.map { it.slot })
    }

    @Test
    fun `list reports deactivatedAt for inactive users`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)

        val users = UserService.listUsers()

        val target = users.single { it.id == targetUserId.toString() }
        assertEquals("INACTIVE", target.status)
        assertNotNull(target.deactivatedAt)
    }

    private fun deactivatedAt(userId: UUID): OffsetDateTime? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.deactivatedAt]
        }

    private fun userStatus(userId: UUID): UserStatus =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.status]
        }

    private fun latestAuditEntry(userId: UUID): AuditEntry =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where { (AuditLogTable.auditTableName eq "app_user") and (AuditLogTable.recordId eq userId) }
                    .orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()

            AuditEntry(
                action = row[AuditLogTable.action].name,
                changedBy = row[AuditLogTable.changedBy].toString(),
                oldStatus = DatabaseTestHelper.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "status"),
                newStatus = DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "status"),
            )
        }

    private fun auditEntryCount(userId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { (AuditLogTable.auditTableName eq "app_user") and (AuditLogTable.recordId eq userId) }
                .count()
        }

    private data class AuditEntry(
        val action: String,
        val changedBy: String,
        val oldStatus: String,
        val newStatus: String,
    )
}
