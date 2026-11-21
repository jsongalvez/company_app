package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.identity.InviteMintRequest
import com.companyb.companyapp.contracts.identity.PersonStatus
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** An invite's stand-in account is nobody until the code is accepted, and a lapsed invite says so. */
class InviteStandInPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "inviter")
    }

    private fun statusOf(userId: UUID) = UserService.listUsers(callerId).single { it.id == userId.toString() }

    private fun isPerson(userId: UUID) = transaction { UserRepository.isActiveUserInTransaction(userId) }

    private fun expire(userId: UUID) {
        transaction {
            CredentialTokenTable.update({ CredentialTokenTable.userId eq userId }) {
                it[expiresAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    @Test
    fun `an open invite lists as Invited, a lapsed one as Invite expired, and neither is a person`() {
        val userId = UUID.fromString(UserService.mintInvite(callerId, InviteMintRequest()).userId)

        assertEquals(PersonStatus.INVITED, statusOf(userId).status)
        assertNotNull(statusOf(userId).inviteExpiresAt)
        assertFalse(isPerson(userId), "a stand-in must not pass roster or picker eligibility")

        expire(userId)

        assertEquals(PersonStatus.INVITE_EXPIRED, statusOf(userId).status)
        assertFalse(isPerson(userId))
    }

    @Test
    fun `a new code revives a lapsed invite and accepting it makes an active person`() {
        val userId = UUID.fromString(UserService.mintInvite(callerId, InviteMintRequest()).userId)
        expire(userId)

        val fresh = UserService.mintInvite(callerId, InviteMintRequest(userId = userId.toString()))
        assertEquals(PersonStatus.INVITED, statusOf(userId).status)

        AuthService.acceptInvite(
            fresh.inviteCode,
            "valid-password",
            AcceptProfile(username = "joined", email = "joined@example.test", displayName = "Joined"),
        )
        assertEquals(PersonStatus.ACTIVE, statusOf(userId).status)
        assertTrue(isPerson(userId))
        assertFailsWith<ValidationException> {
            UserService.mintInvite(callerId, InviteMintRequest(userId = userId.toString()))
        }
    }
}
