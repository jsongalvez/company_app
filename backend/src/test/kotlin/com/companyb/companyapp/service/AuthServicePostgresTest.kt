package com.companyb.companyapp.service
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.RegistrationConflictException
import com.companyb.companyapp.repository.UserCreateParams
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthServicePostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()

    override fun initTestData() {
        val passwordHash = Password.create("test-password")
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "logout-test-$userId",
            passwordHash = passwordHash,
            email = "${userId.toString().take(8)}@logout-test.st",
            displayName = "Logout Test User",
            status = UserStatus.ACTIVE,
        )
    }

    @Test
    fun `logout denies user and invalidates token`() {
        val token = JwtService.generateToken(userId.toString())

        val beforeLogout = JwtService.verifyToken(token)
        assertNotNull(beforeLogout, "Token should be valid before logout")
        assertEquals(userId.toString(), beforeLogout)

        AuthService.logout(userId)

        val afterLogout = JwtService.verifyToken(token)
        assertNull(afterLogout, "Token should be invalid after logout")
    }

    @Test
    fun `logout is idempotent`() {
        AuthService.logout(userId)
        AuthService.logout(userId)
    }

    @Test
    fun `login after logout issues a fresh token that verifies`() {
        AuthService.logout(userId)

        // #505 — login mints with a DB-derived instant strictly after the boundary,
        // so the fresh token verifies immediately with no second-boundary wait.
        val result = AuthService.login("logout-test-$userId", "test-password", "203.0.113.${userId.toString().take(8)}")

        val token =
            (result as? LoginResult.Success)?.token ?: error("login must succeed after logout for an ACTIVE user")
        assertNotNull(JwtService.verifyToken(token), "fresh token issued after logout must verify")
    }

    @Test
    fun `logout invalidates all tokens for user`() {
        val token1 = JwtService.generateToken(userId.toString())
        val token2 = JwtService.generateToken(userId.toString())

        assertNotNull(JwtService.verifyToken(token1))
        assertNotNull(JwtService.verifyToken(token2))

        AuthService.logout(userId)

        assertNull(JwtService.verifyToken(token1))
        assertNull(JwtService.verifyToken(token2))
    }

    @Test
    fun `registration conflict writes no audit row`() {
        val conflictingUserId = TestFixtures.uuid()

        assertFailsWith<RegistrationConflictException> {
            transaction {
                UserRepository.createUserInTransaction(
                    UserCreateParams(
                        username = "logout-test-$userId",
                        passwordHash = Password.create("test-password"),
                        email = "audit-${userId.toString().take(8)}@example.test",
                        displayName = "Duplicate User",
                    ),
                )
            }
        }

        assertEquals(0L, auditEntryCount(conflictingUserId))
        assertNotNull(UserRepository.findByUsername("logout-test-$userId"))
    }

    private fun auditEntryCount(recordId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "app_user") and (AuditLogTable.recordId eq recordId)
                }.count()
        }
}
