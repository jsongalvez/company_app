package com.companyb.companyapp.api.routes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import org.junit.ClassRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// #349 — public registration is closed (#346 decision 1): the register route is not wired,
// so POST /auth/register must refuse account creation (Javalin default 404 for an
// unregistered path) while the sibling auth routes stay live.
class AuthRouteClosureTest : BasePostgresTest() {
    override fun initTestData() = Unit

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
                AuthRoutes.login(cfg)
                AuthRoutes.logout(cfg)
            }
        }
    }

    @Test
    fun `POST auth-register refuses account creation with 404`() {
        val response =
            testServer.client.post(
                "/auth/register",
                mapOf(
                    "username" to "closure-probe",
                    "password" to "sup3r-secret-pass",
                    "email" to "closure-probe@example.test",
                    "displayName" to "Closure Probe",
                ),
            )

        assertEquals(404, response.code)
        assertNull(UserRepository.findByUsername("closure-probe"), "closed registration must not mint a user")
    }

    @Test
    fun `POST auth-login stays live on the closed-registration deployment`() {
        val response =
            testServer.client.post(
                "/auth/login",
                mapOf(
                    "username" to "no-such-user",
                    "password" to "wrong-password",
                ),
            )

        assertEquals(401, response.code)
    }
}
