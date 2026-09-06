package com.companyb.companyapp.identity

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.CredentialTokenPurpose
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.CredentialTokenRepository
import com.companyb.companyapp.identity.CredentialTokenTable
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.LoginResult
import com.companyb.companyapp.identity.PasswordResetSender
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #353 — forgot-password lifecycle at the service level: request → single-use/expiry
 * semantics → prior credential invalidation + live-JWT denial. Reuses the #350 token
 * storage with the PASSWORD_RESET purpose. Tests mint via [AuthService.mintResetCode]
 * (the raw code never appears in any response, by design).
 */
class PasswordResetFlowPostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private fun newUser(prefix: String): UUID {
        val id = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(id, prefix)
        return id
    }

    private fun username(id: UUID): String =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq id }
                .single()[AppUserTable.username]
        }

    private fun email(id: UUID): String =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq id }
                .single()[AppUserTable.email]
        }

    private fun backdateAllResetTokens(userId: UUID) {
        transaction {
            CredentialTokenTable.update(
                {
                    (CredentialTokenTable.userId eq userId) and
                        (CredentialTokenTable.purpose eq CredentialTokenPurpose.PASSWORD_RESET)
                },
            ) {
                it[expiresAt] = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
            }
        }
    }

    @Test
    fun `unknown identifier mints nothing`() {
        val before = unknownCount()
        assertNull(AuthService.mintResetCode("no-such-user-xyz"), "no account matched")
        assertEquals(before, unknownCount(), "an unmatched identifier must not mint a token")
    }

    private fun unknownCount(): Long =
        transaction {
            CredentialTokenTable
                .selectAll()
                .where {
                    CredentialTokenTable.purpose eq
                        CredentialTokenPurpose.PASSWORD_RESET
                }.count()
        }

    @Test
    fun `request matches by username and by email`() {
        val userId = newUser("reset-lookup")
        assertNotNull(AuthService.mintResetCode(username(userId)), "username leg must mint")
        assertNotNull(AuthService.mintResetCode(email(userId)), "email leg must mint")
    }

    @Test
    fun `a code works exactly once and the new password replaces the old credential`() {
        val userId = newUser("reset-once")
        val liveToken = JwtService.generateToken(userId.toString())
        assertNotNull(JwtService.verifyToken(liveToken), "token must verify before the reset")
        val code = assertNotNull(AuthService.mintResetCode(username(userId)))

        AuthService.resetPassword(code, "valid-password")

        assertIs<LoginResult.Success>(AuthService.login(username(userId), "valid-password", "127.0.0.1"))
        assertNull(JwtService.verifyToken(liveToken), "live JWTs must not survive a reset")

        val error =
            assertFailsWith<ValidationException> {
                AuthService.resetPassword(code, "another-valid-password")
            }
        assertTrue(error.message!!.contains("already been used"), "got: ${error.message}")
    }

    @Test
    fun `expired codes are rejected and weak passwords do not consume a live code`() {
        val userId = newUser("reset-exp")
        val code = assertNotNull(AuthService.mintResetCode(username(userId)))
        backdateAllResetTokens(userId)

        val expired = assertFailsWith<ValidationException> { AuthService.resetPassword(code, "valid-password") }
        assertTrue(expired.message!!.contains("expired"), "got: ${expired.message}")

        val fresh = assertNotNull(AuthService.mintResetCode(username(userId)))
        val weak = assertFailsWith<ValidationException> { AuthService.resetPassword(fresh, "short") }
        assertTrue(weak.message!!.contains("policy"), "got: ${weak.message}")
        // The fresh code survived the weak attempt.
        AuthService.resetPassword(fresh, "valid-password")
    }

    @Test
    fun `re-request supersedes outstanding codes`() {
        val userId = newUser("reset-supersede")
        val first = assertNotNull(AuthService.mintResetCode(username(userId)))
        val second = assertNotNull(AuthService.mintResetCode(username(userId)))

        assertFailsWith<ValidationException> { AuthService.resetPassword(first, "valid-password") }
        AuthService.resetPassword(second, "valid-password")
        assertIs<LoginResult.Success>(AuthService.login(username(userId), "valid-password", "127.0.0.1"))
    }

    @Test
    fun `request and redemption write their audit rows as the account holder`() {
        val userId = newUser("reset-audit")
        val code = assertNotNull(AuthService.mintResetCode(username(userId)))
        AuthService.resetPassword(code, "valid-password")

        val inserted =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "credential_token") and
                            (AuditLogTable.changedBy eq userId) and
                            (AuditLogTable.action eq AuditAction.INSERT)
                    }.count()
            }
        assertTrue(inserted >= 1L, "the self-requested mint audit row must exist")
        assertTrue(auditUpdateCount(userId) >= 2L, "redemption writes user + token UPDATE rows")
    }

    @Test
    fun `SMTP failure leaves issued code redeemable`() {
        val userId = newUser("reset-delivery-failure")
        val expectedRecipient = email(userId)
        var deliveredRecipient: String? = null
        var deliveredCode: String? = null
        val sender =
            PasswordResetSender { recipient, rawCode, _ ->
                deliveredRecipient = recipient
                deliveredCode = rawCode
                error("SMTP unavailable")
            }

        assertTrue(
            AuthService.requestPasswordReset(
                identifier = username(userId),
                ip = "198.51.100.42",
                senderOverride = sender,
            ),
        )

        assertEquals(expectedRecipient, deliveredRecipient)
        AuthService.resetPassword(assertNotNull(deliveredCode), "valid-password")
        assertIs<LoginResult.Success>(AuthService.login(username(userId), "valid-password", "127.0.0.1"))
    }

    private fun auditUpdateCount(changedBy: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.changedBy eq changedBy) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.count()
        }
}
