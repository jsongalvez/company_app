package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthServicePostgresTest : BasePostgresTest() {
    private val userId = UUID.randomUUID()

    override fun initTestData() {
        DenyList.clear()
        val passwordHash = Password.create("test-password")
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "logout-test-$userId",
            passwordHash = passwordHash,
            email = "${userId.toString().take(8)}@logout-test.st",
            displayName = "Logout Test User",
            status = UserStatus.ACTIVE,
        )
        trackOwned(AppUserTable, AppUserTable.id, userId)
    }

    @Test
    fun `logout denies user and invalidates token`() {
        val token = JwtService.generateToken(userId.toString())

        val beforeLogout = JwtService.verifyToken(token)
        assertNotNull(beforeLogout, "Token should be valid before logout")
        assertEquals(userId.toString(), beforeLogout)

        DenyList.deny(userId)

        val afterLogout = JwtService.verifyToken(token)
        assertNull(afterLogout, "Token should be invalid after logout")
    }

    @Test
    fun `logout is idempotent`() {
        DenyList.deny(userId)
        DenyList.deny(userId)
    }

    @Test
    fun `login after deny issues a fresh token that verifies`() {
        // Stamp the deny 5s in the past: JWT iat is second-precision, so any token
        // minted now has iat strictly after the deny — no clock boundary to cross.
        DenyList.denyAt(userId, Instant.now().minusSeconds(5))

        val result = AuthService.login("logout-test-$userId", "test-password", "203.0.113.${userId.toString().take(8)}")

        val token = (result as? LoginResult.Success)?.token ?: error("login must succeed after deny for an ACTIVE user")
        assertNotNull(JwtService.verifyToken(token), "fresh token issued after deny must verify")
    }

    @Test
    fun `logout invalidates all tokens for user`() {
        val token1 = JwtService.generateToken(userId.toString())
        val token2 = JwtService.generateToken(userId.toString())

        assertNotNull(JwtService.verifyToken(token1))
        assertNotNull(JwtService.verifyToken(token2))

        DenyList.deny(userId)

        assertNull(JwtService.verifyToken(token1))
        assertNull(JwtService.verifyToken(token2))
    }
}
