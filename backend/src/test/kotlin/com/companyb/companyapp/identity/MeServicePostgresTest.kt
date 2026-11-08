package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.contracts.authorization.ActsResponse
import com.companyb.companyapp.contracts.authorization.RightsResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
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
    fun `getRights gives a Coordinator member what Access allows at their branch`() {
        val branchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(branchId)
        IdentityFixtures.member(userId, branchId, Role.COORDINATOR)

        val rights = MeService.getRights(userId)

        val here = rights.branches.single { it.id == branchId.toString() }
        val looked = Act.entries.filter { it in rights.everywhere.may || it in here.may }
        assertEquals(Act.entries.filter { Access.allows(userId, it, Place.Branch(branchId)) }, looked)
        assertTrue(Act.CHANGE_CLOSED_DAY in here.may)
    }

    @Test
    fun `getRights gives nothing to a user with no roles`() {
        assertEquals(noRights, MeService.getRights(userId))
    }

    @Test
    fun `getRights gives nothing to inactive users`() {
        assertEquals(noRights, MeService.getRights(inactiveUserId))
    }

    private val noRights = RightsResponse(ActsResponse(emptyList(), emptyList()), emptyList(), emptyList())
}
