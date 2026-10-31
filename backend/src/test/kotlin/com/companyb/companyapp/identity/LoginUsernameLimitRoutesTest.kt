package com.companyb.companyapp.identity

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import io.javalin.Javalin
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guessing one password from many addresses: each address has its own limit, so the account needs one too. */
class LoginUsernameLimitRoutesTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @BeforeTest
    fun reset() = RateLimiter.resetForTest()

    @AfterTest
    fun leaveNoStateBehind() {
        ClientIp.resetForTest()
        RateLimiter.resetForTest()
    }

    private fun signIn(
        username: String,
        address: String,
    ): Int =
        testServer.client
            .post("/auth/login", mapOf("username" to username, "password" to "wrong-password")) {
                it.header("X-Forwarded-For", address)
            }.code

    @Test
    fun `ten wrong passwords for one account from ten addresses stop the eleventh`() {
        repeat(10) { assertEquals(401, signIn("victim", "198.51.100.$it"), "try ${it + 1} is still answered") }

        assertEquals(429, signIn("victim", "198.51.100.200"), "a fresh address must not get an eleventh guess")
        assertEquals(429, signIn("VICTIM", "198.51.100.201"), "changing the case is the same account")
        assertEquals(401, signIn("someone-else", "198.51.100.202"), "other accounts keep their own budget")
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { Database.connect(TestDatabaseLifecycle.requireTestDataSource()) }
                AuthRoutes.login(cfg)
            }
        }
    }
}
