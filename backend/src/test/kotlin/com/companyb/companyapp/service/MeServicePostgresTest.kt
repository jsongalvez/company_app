package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MeServicePostgresTest {
    private val userId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val inactiveUserId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "me-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Me Caller",
        )
        DatabaseTestHelper.insertUser(
            id = inactiveUserId,
            username = "inactive-$inactiveUserId",
            passwordHash = "test-password-hash",
            email = "${inactiveUserId.toString().take(8)}@t.st",
            displayName = "Inactive User",
            status = UserStatus.INACTIVE,
        )
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `getMe returns user data for existing user`() {
        val response = MeService.getMe(userId)

        assertEquals(userId.toString(), response.id)
        assertEquals("me-caller-$userId", response.username)
        assertEquals("ACTIVE", response.status)
        assertTrue(response.createdAt.isNotBlank())
    }

    @Test
    fun `getMe throws NotFoundResponse for non-existent user`() {
        val unknownId = UUID.randomUUID()
        assertFailsWith<NotFoundResponse> {
            MeService.getMe(unknownId)
        }
    }

    @Test
    fun `getMe throws ForbiddenResponse for INACTIVE user`() {
        assertFailsWith<ForbiddenResponse> {
            MeService.getMe(inactiveUserId)
        }
    }

    @Test
    fun `getCapabilities returns capabilities for user with grants`() {
        DatabaseTestHelper.grantEditBranchData(userId, sourceId)
        DatabaseTestHelper.grantManageProducts(userId, sourceId)

        val capabilities = MeService.getCapabilities(userId)

        val codes = capabilities.map { it.capabilityCode }.toSet()
        assertEquals(2, capabilities.size)
        assertTrue("EDIT_BRANCH_DATA" in codes)
        assertTrue("MANAGE_PRODUCTS" in codes)
    }

    @Test
    fun `getCapabilities returns empty list for user with no grants`() {
        val capabilities = MeService.getCapabilities(userId)
        assertTrue(capabilities.isEmpty())
    }

    @Test
    fun `getCapabilities excludes inactive users`() {
        DatabaseTestHelper.grantEditBranchData(inactiveUserId, sourceId)

        val capabilities = MeService.getCapabilities(inactiveUserId)
        assertTrue(capabilities.isEmpty())
    }

    private fun deleteTestRows() {
        transaction {
            UserCapabilityTable.deleteWhere {
                (UserCapabilityTable.userId eq userId) or (UserCapabilityTable.userId eq inactiveUserId)
            }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq userId) or (AppUserTable.id eq inactiveUserId)
            }
        }
    }
}
