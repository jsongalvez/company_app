package com.companyb.companyapp.identity

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A phone off signal for days keeps a replay token that only buys a fresh sign-in, so its queued
 * saves still send. It must never work as an API token, and sign-out must end it.
 */
class ReplayTokenPostgresTest : BasePostgresTest() {
    private val user = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(user, "replay-user")
    }

    @BeforeTest
    fun keys() =
        JwtService.init(
            AppConfig(
                appPort = 0,
                dbHost = "",
                dbPort = "",
                dbName = "",
                dbUser = "",
                dbPassword = "",
                jwtSecret = "x".repeat(64),
                jwtIssuer = "i",
                jwtAudience = "a",
                authDummyPassword = "d",
            ),
        )

    private fun replay() = JwtService.generateReplayToken(user.toString(), 0L, Instant.now().minusSeconds(5))

    @Test
    fun `a replay token is not an API token`() {
        assertNull(JwtService.verifyToken(replay()))
    }

    @Test
    fun `an API token buys no refresh`() {
        assertNull(AuthService.refresh(JwtService.generateToken(user.toString())))
    }

    @Test
    fun `a replay token six days old buys a working sign-in`() {
        val old = JwtService.generateReplayToken(user.toString(), 0L, Instant.now().minusSeconds(6 * 86_400L))
        val fresh = assertNotNull(AuthService.refresh(old))
        assertEquals(user.toString(), JwtService.verifyToken(fresh.first))
        assertNotNull(JwtService.verifyReplayToken(fresh.second))
    }

    @Test
    fun `a replay token past seven days buys nothing`() {
        val old = JwtService.generateReplayToken(user.toString(), 0L, Instant.now().minusSeconds(8 * 86_400L))
        assertNull(AuthService.refresh(old))
    }

    @Test
    fun `signing out ends the replay token`() {
        val token = replay()
        AuthService.logout(user)
        assertNull(AuthService.refresh(token))
    }
}
