package com.companyb.companyapp.identity

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.LoginResult
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #492 — durable JWT revocation through the persisted `jwt_revoked_at` boundary:
 * no process-local state, deterministic signed timestamps, real transactions.
 */
class PersistentRevocationPostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private fun newUser(prefix: String): UUID {
        val id = TestFixtures.uuid()
        IdentityFixtures.insertUser(
            id = id,
            username = "$prefix-${id.toString().take(8)}",
            passwordHash = Password.create("original-password"),
            email = "${id.toString().take(8)}@revoke.st",
            displayName = "Revoke Test User",
            status = com.companyb.companyapp.domain.UserStatus.ACTIVE,
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

    private fun revocationBoundary(userId: UUID): OffsetDateTime? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.jwtRevokedAt]
        }

    private fun craftedToken(
        userId: UUID,
        issuedAt: Instant?,
    ): String {
        val config = AppConfig.parse()
        val builder =
            JWT
                .create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(userId.toString())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
        if (issuedAt != null) builder.withIssuedAt(Date.from(issuedAt))
        return builder.sign(Algorithm.HMAC256(config.jwtSecret))
    }

    @Test
    fun `logout persists the boundary so pre-logout tokens die on a fresh runtime`() {
        val userId = newUser("revoke-logout")
        val liveToken = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(liveToken))

        AuthService.logout(userId)
        assertNotNull(revocationBoundary(userId), "logout must persist the boundary")

        JwtService.init(AppConfig.parse())
        assertNull(JwtService.verifyToken(liveToken), "pre-logout token must die without cache warm-up")

        TestFixtures.waitForNextSecond()
        val fresh = JwtService.generateToken(userId.toString())
        assertEquals(userId.toString(), JwtService.verifyToken(fresh))
    }

    @Test
    fun `same-second issuance stays denied while a later token verifies`() {
        val userId = newUser("revoke-samesec")
        AuthService.logout(userId)
        val boundary = assertNotNull(revocationBoundary(userId))

        val sameSecond = craftedToken(userId, boundary.toInstant())
        assertNull(JwtService.verifyToken(sameSecond), "second-precision iat cannot prove after-boundary")

        val later = craftedToken(userId, boundary.toInstant().plusSeconds(2))
        assertEquals(userId.toString(), JwtService.verifyToken(later))
    }

    @Test
    fun `missing issued-at claim is rejected`() {
        val userId = newUser("revoke-noiat")
        assertNull(JwtService.verifyToken(craftedToken(userId, null)), "iat-less tokens must not verify")
    }

    @Test
    fun `failed reset rolls back password, token, audit, and boundary together`() {
        val userId = newUser("revoke-rollback")
        val code = assertNotNull(AuthService.mintResetCode(usernameOf(userId)))

        assertFailsWith<ValidationException> { AuthService.resetPassword(code, "short") }

        assertNull(revocationBoundary(userId), "failed reset must not advance the boundary")
        assertIs<LoginResult.Success>(AuthService.login(usernameOf(userId), "original-password", "127.0.0.1"))
        AuthService.resetPassword(code, "valid-password")
        assertIs<LoginResult.Success>(AuthService.login(usernameOf(userId), "valid-password", "127.0.0.1"))
    }

    @Test
    fun `concurrent revocations never lower the boundary`() {
        val userId = newUser("revoke-race")
        val oldToken = JwtService.generateToken(userId.toString())

        val failures = mutableListOf<Throwable>()
        val workers =
            List(2) {
                thread {
                    try {
                        AuthService.logout(userId)
                    } catch (failure: Throwable) {
                        synchronized(failures) { failures += failure }
                    }
                }
            }
        workers.forEach { it.join() }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        val first = assertNotNull(revocationBoundary(userId))
        assertNull(JwtService.verifyToken(oldToken))

        AuthService.logout(userId)
        val second = assertNotNull(revocationBoundary(userId))
        assertTrue(!second.isBefore(first), "boundary must never move backward")
    }
}
