package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Every authenticated request verifies its token against the user's current status, revocation
 * boundary and credential version. The result may be cached for a few seconds, but revocation
 * must still take effect on the very next request.
 */
class AuthStateCachePostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private fun newUser(prefix: String): UUID {
        val id = TestFixtures.uuid()
        IdentityFixtures.insertUser(
            id = id,
            username = "$prefix-${id.toString().take(8)}",
            passwordHash = Password.create("original-password"),
            email = "${id.toString().take(8)}@cache.st",
            displayName = "Cache Test User",
            status = UserStatus.ACTIVE,
        )
        return id
    }

    private fun usernameOf(userId: UUID): String =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.username]
        }

    private fun queriesDuring(block: () -> Unit): Int {
        val counted = Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()))
        val previous = TransactionManager.defaultDatabase
        TransactionManager.defaultDatabase = counted
        RequestQueryTally.startRequest()
        try {
            block()
            return RequestQueryTally.snapshot().queries
        } finally {
            RequestQueryTally.endRequest()
            TransactionManager.defaultDatabase = previous
        }
    }

    @Test
    fun `a second request with the same token costs no database query`() {
        val userId = newUser("cache-hit")
        val token = JwtService.generateToken(userId.toString())

        assertEquals(1, queriesDuring { assertNotNull(JwtService.verifyToken(token)) }, "first request reads once")
        assertEquals(0, queriesDuring { assertNotNull(JwtService.verifyToken(token)) }, "second request is cached")
    }

    @Test
    fun `logout takes effect on the next request`() {
        val userId = newUser("cache-logout")
        val token = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(token))

        AuthService.logout(userId)

        assertNull(JwtService.verifyToken(token))
    }

    @Test
    fun `deactivation and reactivation take effect on the next request`() {
        val admin = newUser("cache-admin")
        val userId = newUser("cache-deactivate")
        TestFixtures.waitForNextSecond()
        val token = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(token))

        UserService.deactivate(admin, userId)
        assertNull(JwtService.verifyToken(token), "deactivated user is refused at once")

        UserService.reactivate(admin, userId)
        TestFixtures.waitForNextSecond()
        val fresh = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(fresh), "reactivated user is accepted at once")
    }

    @Test
    fun `a password reset kills the old token on the next request`() {
        val userId = newUser("cache-reset")
        val token = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(token))
        val username = usernameOf(userId)
        val code = assertNotNull(AuthService.mintResetCode(username))

        AuthService.resetPassword(code, "valid-password")

        assertNull(JwtService.verifyToken(token))
    }
}
