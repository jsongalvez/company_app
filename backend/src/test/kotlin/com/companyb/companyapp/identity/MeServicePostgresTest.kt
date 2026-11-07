package com.companyb.companyapp.identity
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.AccessFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeServicePostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()
    private val inactiveUserId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertUser(
            id = userId,
            username = "me-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Me Caller",
        )
        IdentityFixtures.insertUser(
            id = inactiveUserId,
            username = "inactive-$inactiveUserId",
            passwordHash = "test-password-hash",
            email = "${inactiveUserId.toString().take(8)}@t.st",
            displayName = "Inactive User",
            status = UserStatus.INACTIVE,
        )
    }

    @Test
    fun `getMe returns user data for existing user`() {
        val response = MeService.getMe(userId)

        assertEquals(userId.toString(), response.id)
        assertEquals("me-caller-$userId", response.username)
        // #381 — the display name rides on the identity read.
        assertEquals("Me Caller", response.displayName)
        assertEquals(com.companyb.companyapp.contracts.identity.UserStatus.ACTIVE, response.status)
        assertTrue(response.createdAt.isNotBlank())
    }

    @Test
    fun `getMe throws NotFoundException for non-existent user`() {
        val unknownId = TestFixtures.uuid()
        assertFailsWith<NotFoundException> {
            MeService.getMe(unknownId)
        }
    }

    @Test
    fun `getMe throws ForbiddenException for INACTIVE user`() {
        assertFailsWith<ForbiddenException> {
            MeService.getMe(inactiveUserId)
        }
    }

    @Test
    fun `getCapabilities returns a practitioner member's capabilities`() {
        AccessFixtures.allow(userId, Act.WORK_ANYWHERE)

        val capabilities = MeService.getCapabilities(userId)

        assertEquals(
            setOf(
                CapabilityCodes.MANAGE_CATALOG,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityCodes.VOID_SESSION,
                CapabilityCodes.VIEW_COMMISSION,
            ),
            capabilities.map { it.capabilityCode }.toSet(),
        )
    }

    @Test
    fun `getCapabilities returns empty list for user with no grants`() {
        val capabilities = MeService.getCapabilities(userId)
        assertTrue(capabilities.isEmpty())
    }

    @Test
    fun `getCapabilities excludes inactive users`() {
        AccessFixtures.allow(inactiveUserId, Act.WORK_ANYWHERE)

        val capabilities = MeService.getCapabilities(inactiveUserId)
        assertTrue(capabilities.isEmpty())
    }
}
