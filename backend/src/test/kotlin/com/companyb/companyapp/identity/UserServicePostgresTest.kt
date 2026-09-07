package com.companyb.companyapp.identity
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.CapabilityRepository
import com.companyb.companyapp.authorization.CapabilityService.GLOBAL_CONTEXT_ID
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.RoleRepository
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "caller")
        IdentityFixtures.insertTestUser(targetUserId, "target")
    }

    @Test
    fun `deactivate persists inactive status, writes audit log, and rejects existing token`() {
        IdentityFixtures.grantManageUsers(callerId, sourceId)
        val targetToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)

        assertEquals(UserStatus.INACTIVE, userStatus(targetUserId))
        assertNotNull(jwtRevokedAt(targetUserId), "deactivation must persist the revocation boundary")
        assertNull(JwtService.verifyToken(targetToken))

        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals(callerId.toString(), auditEntry.changedBy)
        assertEquals("ACTIVE", auditEntry.oldStatus)
        assertEquals("INACTIVE", auditEntry.newStatus)
    }

    @Test
    fun `deactivate without MANAGE_USERS is allowed at service layer`() {
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
        UserService.deactivate(callerId, targetUserId)

        assertNotNull(deactivatedAt(targetUserId))
    }

    @Test
    fun `deactivating an already-inactive user is a no-op without audit row or timestamp reset`() {
        UserService.deactivate(callerId, targetUserId)
        val firstStamp = deactivatedAt(targetUserId)
        assertNotNull(firstStamp)

        UserService.deactivate(callerId, targetUserId)

        assertEquals(1L, auditEntryCount(targetUserId))
        assertEquals(firstStamp, deactivatedAt(targetUserId))
    }

    @Test
    fun `reactivate flips status back to ACTIVE and clears deactivatedAt with audit`() {
        UserService.deactivate(callerId, targetUserId)

        UserService.reactivate(callerId, targetUserId)

        assertEquals(UserStatus.ACTIVE, userStatus(targetUserId))
        assertNull(deactivatedAt(targetUserId))
        assertNotNull(
            jwtRevokedAt(targetUserId),
            "old tokens stay dead — reactivation never clears the persisted boundary",
        )
        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals("INACTIVE", auditEntry.oldStatus)
        assertEquals("ACTIVE", auditEntry.newStatus)
        assertEquals(2L, auditEntryCount(targetUserId))
    }

    @Test
    fun `reactivated user can authenticate with a fresh token while the pre-deactivation token stays dead`() {
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

        // JWT iat is second-precision: a token generated in the same second as the
        // revocation is indistinguishable from a pre-revocation token and stays denied.
        waitForNextSecond()
        val freshToken = JwtService.generateToken(targetUserId.toString())
        assertEquals(
            targetUserId.toString(),
            JwtService.verifyToken(freshToken),
            "fresh token issued after reactivation must verify",
        )
    }

    @Test
    fun `persisted revocation survives fresh JwtService init after reactivation`() {
        val oldToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)
        UserService.reactivate(callerId, targetUserId)
        JwtService.init(AppConfig.parse())

        assertNull(JwtService.verifyToken(oldToken), "old token must stay dead without any cache warm-up")
        waitForNextSecond()
        val freshToken = JwtService.generateToken(targetUserId.toString())
        assertEquals(targetUserId.toString(), JwtService.verifyToken(freshToken))
    }

    private fun waitForNextSecond() {
        TestFixtures.waitForNextSecond()
    }

    @Test
    fun `reactivate of already-active user is a no-op without audit row`() {
        UserService.reactivate(callerId, targetUserId)

        assertEquals(UserStatus.ACTIVE, userStatus(targetUserId))
        assertEquals(0L, auditEntryCount(targetUserId))
    }

    @Test
    fun `reactivate of missing user throws not found`() {
        assertFailsWith<NotFoundException> { UserService.reactivate(callerId, TestFixtures.uuid()) }
    }

    @Test
    fun `list returns users with active assignments only, ordered by displayName then username`() {
        val branchA = TestFixtures.uuid()
        val branchB = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(branchA, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Branch B")

        BranchWorkforceFixtures.insertTestAssignment(
            userId = targetUserId,
            branchId = branchA,
            slot = 2,
            assignedBy = callerId,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = targetUserId,
            branchId = branchB,
            slot = 1,
            assignedBy = callerId,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = callerId,
            branchId = branchA,
            slot = 9,
            assignedBy = callerId,
            ended = true,
        )

        val users = UserService.listUsers()

        assertEquals(2, users.size)
        assertEquals("Test caller", users[0].displayName)
        assertEquals("Test target", users[1].displayName)
        assertEquals(emptyList(), users[0].assignments, "ended assignments must be excluded")

        val target = users[1]
        assertEquals(com.companyb.companyapp.contracts.identity.UserStatus.ACTIVE, target.status)
        assertNull(target.deactivatedAt)
        assertEquals(listOf(branchB.toString(), branchA.toString()), target.assignments.map { it.branchId })
        assertEquals(listOf("Branch B", "Branch A"), target.assignments.map { it.branchName })
        assertEquals(listOf<Short>(1, 2), target.assignments.map { it.slot })
    }

    @Test
    fun `list reports deactivatedAt for inactive users`() {
        UserService.deactivate(callerId, targetUserId)

        val users = UserService.listUsers()

        val target = users.single { it.id == targetUserId.toString() }
        assertEquals(com.companyb.companyapp.contracts.identity.UserStatus.INACTIVE, target.status)
        assertNotNull(target.deactivatedAt)
    }

    // ──────────────────────────────────────────────
    // #344 — role assignment (creation moved to the
    // invite flow, #350 — see InviteFlowPostgresTest)
    // ──────────────────────────────────────────────

    @Test
    fun `replaceRoles assigns seeded bundles whose capabilities derive through the view`() {
        UserService.replaceRoles(callerId, targetUserId, listOf("OWNER"))

        assertEquals(listOf("OWNER"), transaction { RoleRepository.findRoleNamesForUserInTransaction(targetUserId) })
        assertTrue(
            CapabilityRepository.hasCapability(
                targetUserId,
                "MANAGE_USERS",
                CapabilityContextType.GLOBAL,
                GLOBAL_CONTEXT_ID,
            ),
            "user_role row must derive MANAGE_USERS through the capability view",
        )
        assertEquals(1L, auditActionCount("user_role", AuditAction.UPDATE, targetUserId))
    }

    @Test
    fun `replaceRoles rejects unknown role names`() {
        assertFailsWith<ValidationException> {
            UserService.replaceRoles(callerId, targetUserId, listOf("OWNER", "NOT_A_ROLE"))
        }
        assertTrue(transaction { RoleRepository.findRoleNamesForUserInTransaction(targetUserId).isEmpty() })
    }

    @Test
    fun `replaceRoles rejects SUPERUSER grant and removal`() {
        assertFailsWith<ValidationException> {
            UserService.replaceRoles(callerId, targetUserId, listOf("SUPERUSER"))
        }

        transaction {
            RoleRepository.assignRoleInTransaction(
                targetUserId,
                RoleRepository.findIdByNameInTransaction("SUPERUSER")!!,
            )
        }
        assertFailsWith<ValidationException> {
            UserService.replaceRoles(callerId, targetUserId, listOf("OWNER"))
        }
        assertEquals(
            listOf("SUPERUSER"),
            transaction { RoleRepository.findRoleNamesForUserInTransaction(targetUserId) },
        )
    }

    @Test
    fun `replaceRoles is idempotent without a second audit row`() {
        UserService.replaceRoles(callerId, targetUserId, listOf("MANAGER", "COORDINATOR"))
        UserService.replaceRoles(callerId, targetUserId, listOf("COORDINATOR", "MANAGER"))

        assertEquals(1L, auditActionCount("user_role", AuditAction.UPDATE, targetUserId))
    }

    @Test
    fun `replaceRoles rewrites roles of a deactivated user following the deactivate precedent`() {
        UserService.deactivate(callerId, targetUserId)

        UserService.replaceRoles(callerId, targetUserId, listOf("PRACTITIONER"))

        assertEquals(
            listOf("PRACTITIONER"),
            transaction { RoleRepository.findRoleNamesForUserInTransaction(targetUserId) },
        )
    }

    @Test
    fun `replaceRoles on a missing user throws not found`() {
        assertFailsWith<NotFoundException> { UserService.replaceRoles(callerId, TestFixtures.uuid(), listOf("OWNER")) }
    }

    @Test
    fun `listUsers reports each user's role names and getRoles hides SUPERUSER`() {
        transaction {
            RoleRepository.assignRoleInTransaction(targetUserId, RoleRepository.findIdByNameInTransaction("OWNER")!!)
        }

        val summary = UserService.listUsers().single { it.id == targetUserId.toString() }
        assertEquals(listOf("OWNER"), summary.roles)

        val roleNames = UserService.getRoles().map { it.name }
        assertFalse(roleNames.contains("SUPERUSER"), "SUPERUSER must not be offered to pickers")
        assertTrue(roleNames.contains("ONBOARDING"))
        val ownerResponse = UserService.getRoles().single { it.name == "OWNER" }
        assertTrue(ownerResponse.capabilities.contains("MANAGE_USERS"))
    }

    private fun auditActionCount(
        tableName: String,
        action: AuditAction,
        recordId: UUID,
    ): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId) and
                        (AuditLogTable.action eq action)
                }.count()
        }

    private fun deactivatedAt(userId: UUID): OffsetDateTime? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.deactivatedAt]
        }

    private fun jwtRevokedAt(userId: UUID): OffsetDateTime? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.jwtRevokedAt]
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
                oldStatus = TestFixtures.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "status"),
                newStatus = TestFixtures.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "status"),
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
