package com.companyb.companyapp.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.CredentialTokenPurpose
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CredentialTokenTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #505 — credential serialization: JWTs bind to the credential generation verified,
 * and all credential mutations serialize on the account lock.
 *
 * Deterministic barrier-controlled Postgres tests (no sleeps): latches force the
 * overlapping interleaving, threads prove single-winner invariants.
 */
class CredentialSerializationPostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private fun newUser(
        prefix: String,
        password: String = "old-password-1",
        status: UserStatus = UserStatus.ACTIVE,
    ): UUID {
        val id = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = id,
            username = "$prefix-${id.toString().take(8)}",
            passwordHash = Password.create(password),
            email = "${id.toString().take(8)}@cred505.st",
            displayName = "Cred505 User",
            status = status,
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

    private fun liveResetCount(userId: UUID): Long =
        transaction {
            CredentialTokenTable
                .selectAll()
                .where {
                    (CredentialTokenTable.userId eq userId) and
                        (CredentialTokenTable.purpose eq CredentialTokenPurpose.PASSWORD_RESET) and
                        CredentialTokenTable.consumedAt.isNull()
                }.count()
        }

    private fun liveInviteCount(userId: UUID): Long =
        transaction {
            CredentialTokenTable
                .selectAll()
                .where {
                    (CredentialTokenTable.userId eq userId) and
                        (CredentialTokenTable.purpose eq CredentialTokenPurpose.INVITE) and
                        CredentialTokenTable.consumedAt.isNull()
                }.count()
        }

    private fun versionOf(userId: UUID): Long =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.credentialVersion]
        }

    private fun forgedTokenWithVersion(
        userId: UUID,
        version: Long,
        issuedAt: Instant,
    ): String {
        val config = AppConfig.parse()
        return JWT
            .create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(userId.toString())
            .withExpiresAt(Date.from(issuedAt.plus(1, ChronoUnit.DAYS)))
            .withIssuedAt(Date.from(issuedAt))
            .withClaim(JwtService.CREDENTIAL_VERSION_CLAIM, version)
            .sign(Algorithm.HMAC256(config.jwtSecret))
    }

    @Test
    fun `old password read before reset cannot mint a session after reset commits`() {
        val userId = newUser("cred-race", "old-password-1")
        val username = usernameOf(userId)
        val preResetToken = (AuthService.login(username, "old-password-1", "10.5.0.1") as LoginResult.Success).token
        assertNotNull(JwtService.verifyToken(preResetToken))

        // Force the race: login's initial hash read happens before the reset commits,
        // the login finish happens after.
        val loginHasRead = CountDownLatch(1)
        val resetDone = CountDownLatch(1)
        val loginResult = ConcurrentLinkedQueue<LoginResult>()
        val failures = ConcurrentLinkedQueue<Throwable>()

        val loginThread =
            thread {
                try {
                    assertNotNull(UserRepository.findByUsername(username), "initial read must see the account")
                    loginHasRead.countDown()
                    assertTrue(resetDone.await(30, TimeUnit.SECONDS), "reset must commit")
                    loginResult += AuthService.login(username, "old-password-1", "10.5.0.2")
                } catch (failure: Throwable) {
                    failures += failure
                }
            }
        assertTrue(loginHasRead.await(30, TimeUnit.SECONDS), "login must read before reset")
        val code = assertNotNull(AuthService.mintResetCode(username))
        AuthService.resetPassword(code, "new-password-1")
        resetDone.countDown()
        loginThread.join(30_000)

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertEquals(1, loginResult.size)
        assertIs<LoginResult.InvalidCredentials>(loginResult.single(), "stale hash must fail closed after rotation")

        assertNull(JwtService.verifyToken(preResetToken), "pre-reset token must die on version rotation")

        // A forged token with the stale generation but a fresh iat still fails: version is load-bearing.
        val forged = forgedTokenWithVersion(userId, versionOf(userId) - 1, Instant.now().plusSeconds(5))
        assertNull(JwtService.verifyToken(forged), "stale generation must not verify even with a fresh iat")

        // Fresh credential works immediately on two independent verifier instances.
        val fresh = assertIs<LoginResult.Success>(AuthService.login(username, "new-password-1", "10.5.0.3"))
        assertNotNull(JwtService.verifyToken(fresh.token))
        JwtService.init(AppConfig.parse())
        assertEquals(userId.toString(), JwtService.verifyToken(fresh.token))
    }

    @Test
    fun `concurrent reset mints leave exactly one live code`() {
        val userId = newUser("cred-mint-race", "mint-password-1")
        val username = usernameOf(userId)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val minted = ConcurrentLinkedQueue<String>()
        val failures = ConcurrentLinkedQueue<Throwable>()

        repeat(2) {
            thread {
                try {
                    assertTrue(start.await(30, TimeUnit.SECONDS))
                    minted += assertNotNull(AuthService.mintResetCode(username))
                } catch (failure: Throwable) {
                    failures += failure
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "both mints must finish")

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertEquals(2, minted.size)
        assertEquals(1L, liveResetCount(userId), "concurrent mints must converge to one live code")

        // Exactly one of the two codes redeems; the superseded one names its failure.
        val codes = minted.toList()
        var redeemed = 0
        var rejected = 0
        codes.forEach { code ->
            try {
                AuthService.resetPassword(code, "mint-password-2")
                redeemed++
            } catch (e: ValidationException) {
                assertNotNull(e.message, "superseded mint must name its failure")
                rejected++
            }
        }
        assertEquals(1, redeemed, "exactly one mint must survive")
        assertEquals(1, rejected, "the superseded mint must reject")
    }

    @Test
    fun `re-invite supersedes then accept wins only on the live code`() {
        val callerId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(callerId, "cred-inviter")
        val first =
            UserService.mintInvite(
                callerId,
                InviteMintRequest("cred-inv-1", "cred-inv-1@example.test", "Invited", emptyList()),
            )
        val targetId = UUID.fromString(first.userId)

        val second =
            UserService.mintInvite(
                callerId,
                InviteMintRequest("cred-inv-1", "cred-inv-1@example.test", "Invited", emptyList()),
            )
        assertEquals(first.userId, second.userId)
        assertEquals(1L, liveInviteCount(targetId), "re-invite must leave one live code")

        assertFailsWith<ValidationException> { AuthService.acceptInvite(first.inviteCode, "valid-password") }
        AuthService.acceptInvite(second.inviteCode, "valid-password")
        assertEquals(0L, liveInviteCount(targetId))

        // Consumed history conflicts instead of minting again.
        assertFailsWith<ConflictException> {
            UserService.mintInvite(
                callerId,
                InviteMintRequest("cred-inv-1", "other@example.test", "Dup"),
            )
        }

        // Accept rotates the generation and kills pre-accept tokens across verifier instances.
        val stale = forgedTokenWithVersion(targetId, versionOf(targetId) - 1, Instant.now().plusSeconds(5))
        assertNull(JwtService.verifyToken(stale))
        val fresh = assertIs<LoginResult.Success>(AuthService.login("cred-inv-1", "valid-password", "10.5.0.4"))
        assertNotNull(JwtService.verifyToken(fresh.token))
        JwtService.init(AppConfig.parse())
        assertEquals(targetId.toString(), JwtService.verifyToken(fresh.token))
    }

    @Test
    fun `concurrent accept and re-invite admit exactly one winner`() {
        val callerId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(callerId, "cred-racer")
        val first =
            UserService.mintInvite(
                callerId,
                InviteMintRequest("cred-inv-race", "cred-inv-race@example.test", "Invited", emptyList()),
            )
        val targetId = UUID.fromString(first.userId)

        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val acceptOk = ConcurrentLinkedQueue<Boolean>()
        val remintOk = ConcurrentLinkedQueue<Boolean>()

        thread {
            try {
                assertTrue(start.await(30, TimeUnit.SECONDS))
                AuthService.acceptInvite(first.inviteCode, "race-password-1")
                acceptOk += true
            } catch (e: ValidationException) {
                assertNotNull(e.message)
                acceptOk += false
            } catch (e: ConflictException) {
                assertNotNull(e.message)
                acceptOk += false
            } finally {
                done.countDown()
            }
        }
        thread {
            try {
                assertTrue(start.await(30, TimeUnit.SECONDS))
                UserService.mintInvite(
                    callerId,
                    InviteMintRequest("cred-inv-race", "cred-inv-race@example.test", "Invited", emptyList()),
                )
                remintOk += true
            } catch (e: ValidationException) {
                assertNotNull(e.message)
                remintOk += false
            } catch (e: ConflictException) {
                assertNotNull(e.message)
                remintOk += false
            } finally {
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))

        assertEquals(1, acceptOk.size)
        assertEquals(1, remintOk.size)
        // Exactly one path wins: accept XOR re-invite.
        assertTrue(acceptOk.single() != remintOk.single(), "accept and re-invite must have exactly one winner")
        assertTrue(liveInviteCount(targetId) <= 1L, "at most one live invite may survive the race")
    }

    @Test
    fun `immediate fresh login after reset and logout verifies, inactive cannot log in`() {
        val userId = newUser("cred-immediate", "immediate-111")
        val username = usernameOf(userId)

        val resetCode = assertNotNull(AuthService.mintResetCode(username))
        AuthService.resetPassword(resetCode, "immediate-222")

        // No sleeps: the login-minted iat is DB-derived strictly after the boundary.
        val afterReset = assertIs<LoginResult.Success>(AuthService.login(username, "immediate-222", "10.5.1.1"))
        assertEquals(userId.toString(), JwtService.verifyToken(afterReset.token))
        JwtService.init(AppConfig.parse())
        assertEquals(userId.toString(), JwtService.verifyToken(afterReset.token), "second verifier must agree")

        AuthService.logout(userId)
        val afterLogout = assertIs<LoginResult.Success>(AuthService.login(username, "immediate-222", "10.5.1.2"))
        assertEquals(userId.toString(), JwtService.verifyToken(afterLogout.token))
        JwtService.init(AppConfig.parse())
        assertEquals(userId.toString(), JwtService.verifyToken(afterLogout.token))

        // INACTIVE cannot log in and yields no usable token.
        transaction {
            AppUserTable.update({ AppUserTable.id eq userId }) { it[status] = UserStatus.INACTIVE }
        }
        assertIs<LoginResult.InvalidCredentials>(AuthService.login(username, "immediate-222", "10.5.1.3"))
        assertNull(JwtService.verifyToken(afterLogout.token), "deactivation must kill live tokens")
        JwtService.init(AppConfig.parse())
        assertNull(JwtService.verifyToken(afterLogout.token))
    }
}
