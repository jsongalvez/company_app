package com.companyb.companyapp.identity

import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.CredentialTokenPurpose
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.CredentialTokenRepository
import com.companyb.companyapp.identity.CredentialTokenTable
import com.companyb.companyapp.identity.LoginResult
import com.companyb.companyapp.identity.RoleRepository
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #350 — invite-link account creation lifecycle at the service level: mint → accept →
 * single-use/expiry semantics, re-invite recovery, guards, and audit rows.
 */
class InviteFlowPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "inviter")
    }

    private fun mint(
        username: String,
        email: String,
        displayName: String = "Invited User",
        roles: List<String> = emptyList(),
    ) = UserService.mintInvite(callerId, InviteMintRequest(username, email, displayName, roles))

    private fun backdateAllInvites(userId: UUID) {
        transaction {
            CredentialTokenTable.update({ CredentialTokenTable.userId eq userId }) {
                it[expiresAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    private fun consumeAllInvites(userId: UUID) {
        transaction {
            CredentialTokenRepository
                .findUnconsumedIdsInTransaction(userId, CredentialTokenPurpose.INVITE)
                .forEach { CredentialTokenRepository.invalidateInTransaction(it) }
        }
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

    private fun tokenAuditCount(
        action: AuditAction,
        userId: UUID,
    ): Long =
        transaction {
            val tokenIds =
                CredentialTokenTable
                    .selectAll()
                    .where { CredentialTokenTable.userId eq userId }
                    .map { it[CredentialTokenTable.id] }
            if (tokenIds.isEmpty()) {
                0L
            } else {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "credential_token") and
                            (AuditLogTable.recordId inList tokenIds) and
                            (AuditLogTable.action eq action)
                    }.count()
            }
        }

    @Test
    fun `mint creates an account nobody can log into until the code is accepted`() {
        val minted = mint("invitee-1", "invitee-1@example.test")
        val userId = UUID.fromString(minted.userId)

        assertEquals(
            LoginResult.InvalidCredentials,
            AuthService.login("invitee-1", "any-password", "127.0.0.1"),
            "the random unusable hash must reject login before acceptance",
        )

        AuthService.acceptInvite(minted.inviteCode, "valid-password")

        assertIs<LoginResult.Success>(AuthService.login("invitee-1", "valid-password", "127.0.0.1"))
    }

    @Test
    fun `a code works exactly once — reuse names the failure`() {
        val minted = mint("invitee-2", "invitee-2@example.test")
        AuthService.acceptInvite(minted.inviteCode, "valid-password")

        val error =
            assertFailsWith<ValidationException> {
                AuthService.acceptInvite(minted.inviteCode, "another-valid-password")
            }
        assertTrue(
            error.message!!.contains("already been used"),
            "reuse must say the code was used, got: ${error.message}",
        )
    }

    @Test
    fun `an expired code is rejected with the expired message`() {
        val minted = mint("invitee-3", "invitee-3@example.test")
        val userId = UUID.fromString(minted.userId)
        backdateAllInvites(userId)

        val error =
            assertFailsWith<ValidationException> {
                AuthService.acceptInvite(minted.inviteCode, "valid-password")
            }
        assertTrue(error.message!!.contains("expired"), "got: ${error.message}")
    }

    @Test
    fun `an unknown code is invalid and a weak password does not consume a live code`() {
        val invalid =
            assertFailsWith<ValidationException> { AuthService.acceptInvite("no-such-code", "valid-password") }
        assertTrue(invalid.message!!.contains("invalid"))

        val minted = mint("invitee-4", "invitee-4@example.test")
        val weak =
            assertFailsWith<ValidationException> { AuthService.acceptInvite(minted.inviteCode, "short") }
        assertTrue(weak.message!!.contains("policy"))
        // The code survived the failed attempt.
        AuthService.acceptInvite(minted.inviteCode, "valid-password")
    }

    @Test
    fun `re-invite recovers the existing account, kills the old code, and applies the new roles`() {
        val first = mint("invitee-5", "invitee-5@example.test", roles = listOf("PRACTITIONER"))
        val userId = UUID.fromString(first.userId)

        val second =
            UserService.mintInvite(
                callerId,
                InviteMintRequest(
                    username = "invitee-5",
                    email = "changed-address@example.test",
                    displayName = "Invited User",
                    roles = listOf("COORDINATOR"),
                ),
            )
        assertEquals(first.userId, second.userId, "re-invite must target the existing account")
        assertNotEquals(first.inviteCode, second.inviteCode)

        assertFailsWith<ValidationException> { AuthService.acceptInvite(first.inviteCode, "valid-password") }
        AuthService.acceptInvite(second.inviteCode, "valid-password")

        val roles =
            transaction {
                RoleRepository.findRoleNamesForUserInTransaction(UUID.fromString(first.userId))
            }
        assertEquals(listOf("COORDINATOR"), roles)
    }

    @Test
    fun `duplicate identity without an outstanding link conflicts instead of re-inviting`() {
        val first = mint("invitee-6", "invitee-6@example.test")
        val userId = UUID.fromString(first.userId)
        // Simulate a fully consumed history: no unconsumed link remains.
        consumeAllInvites(userId)

        assertFailsWith<ConflictException> {
            UserService.mintInvite(
                callerId,
                InviteMintRequest("invitee-6", "other@example.test", "Dup"),
            )
        }
    }

    @Test
    fun `mint rejects SUPERUSER bundles and unknown roles without writing users`() {
        assertFailsWith<ValidationException> {
            mint("guarded-su", "guarded-su@example.test", roles = listOf("SUPERUSER"))
        }
        assertFailsWith<ValidationException> {
            mint("guarded-unknown", "guarded-unknown@example.test", roles = listOf("NOT_A_ROLE"))
        }
        assertNull(UserRepository.findByUsername("guarded-su"))
        assertNull(UserRepository.findByUsername("guarded-unknown"))
    }

    @Test
    fun `mint and accept write their audit rows`() {
        val minted = mint("audited-invitee", "audited-invitee@example.test")
        val userId = UUID.fromString(minted.userId)

        assertEquals(1L, auditActionCount("app_user", AuditAction.INSERT, userId))
        assertEquals(1L, tokenAuditCount(AuditAction.INSERT, userId))

        AuthService.acceptInvite(minted.inviteCode, "valid-password")

        assertEquals(1L, tokenAuditCount(AuditAction.UPDATE, userId))
        assertTrue(auditActionCount("app_user", AuditAction.UPDATE, userId) >= 1L)
    }
}
