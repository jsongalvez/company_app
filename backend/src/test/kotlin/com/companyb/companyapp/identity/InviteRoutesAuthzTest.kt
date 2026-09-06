package com.companyb.companyapp.identity

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #350 — invite routes over HTTP: the GLOBAL MANAGE_USERS gate on POST /api/invites
 * (ADR-0007) and the public, code-as-authorization shape of POST /auth/accept-invite.
 */
class InviteRoutesAuthzTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @Test
    fun `minting an invite without MANAGE_USERS is forbidden`() {
        val caller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(caller, "no-capability-caller")

        val response =
            testServer.client.post(
                "/api/invites",
                mapOf(
                    "username" to "x-403",
                    "email" to "x403@example.test",
                    "displayName" to "X",
                ),
                asUser(caller),
            )

        assertEquals(403, response.code)
    }

    @Test
    fun `manager mints over http, the invitee accepts unauthenticated, then logs in`() {
        val caller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(caller, "http-manager")
        DatabaseTestHelper.grantManageUsers(caller, TestFixtures.uuid())
        // The server-side mint authors its audit rows as the caller — they FK app_user.

        val mintResponse =
            testServer.client.post(
                "/api/invites",
                mapOf(
                    "username" to "http-invitee",
                    "email" to "http-invitee@example.test",
                    "displayName" to "HTTP Invitee",
                ),
                asUser(caller),
            )
        assertEquals(201, mintResponse.code)
        val responseBody = Json.parseToJsonElement(mintResponse.body.string()).jsonObject
        val inviteCode = responseBody["inviteCode"]!!.jsonPrimitive.content

        // No auth headers: the code IS the authorization.
        val acceptResponse =
            testServer.client.post(
                "/auth/accept-invite",
                mapOf("token" to inviteCode, "newPassword" to "valid-password"),
            )
        assertEquals(204, acceptResponse.code)

        val login =
            testServer.client.post(
                "/auth/login",
                mapOf("username" to "http-invitee", "password" to "valid-password"),
            )
        assertEquals(200, login.code)
    }

    @Test
    fun `an invalid code surfaces the backend error body inline`() {
        val response =
            testServer.client.post(
                "/auth/accept-invite",
                mapOf("token" to "not-a-real-code", "newPassword" to "valid-password"),
            )
        assertEquals(400, response.code)
        val message =
            Json
                .parseToJsonElement(response.body.string())
                .jsonObject["error"]!!
                .jsonPrimitive.content
        kotlin.test.assertTrue(message.contains("invalid"), "got: $message")
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                UserRoutes.register(cfg)
                AuthRoutes.acceptInvite(cfg)
                AuthRoutes.login(cfg)
            }
        }
    }
}
