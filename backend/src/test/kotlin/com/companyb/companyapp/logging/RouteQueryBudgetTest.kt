package com.companyb.companyapp.logging

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.configureAuth
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.registerAllRoutes
import com.companyb.companyapp.registerExceptionHandlers
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hot routes run at most [CEILINGS] statements each, counted the way prod counts them
 * (`queries` on `request_completed`). Raise a ceiling only with the reason in the commit:
 * `tools/perf/sql-trace.py --at` shows which reads a route repeats.
 */
class RouteQueryBudgetTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val staff = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val username = "budget-${staff.toString().take(8)}"

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId, "Budget $branchId")
        IdentityFixtures.insertUser(
            staff,
            username,
            Password.create(PASSWORD),
            "${staff.toString().take(8)}@t.st",
            "Budget",
        )
        transaction {
            val roleId = RoleTable.selectAll().where { RoleTable.name eq "MANAGER" }.single()[RoleTable.id]
            UserRoleTable.insert {
                it[UserRoleTable.userId] = staff
                it[UserRoleTable.roleId] = roleId
            }
        }
        BranchWorkforceFixtures.insertTestAssignment(userId = staff, branchId = branchId, slot = 1, assignedBy = staff)
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, staff)
        SessionClientFixtures.insertTestClient(clientId)
    }

    @Test
    fun `hot routes stay within their statement ceilings`() {
        val attendance = UUID.randomUUID()
        val seen = linkedMapOf<String, Int>()

        fun step(
            name: String,
            method: String,
            path: String,
            body: String? = null,
        ) {
            val status = call(method, path, body)
            assertTrue(status in 200..299, "$name answered $status")
            seen[name] = counts.getValue("$method $path")
        }
        step("sign-in", "POST", "/auth/login", """{"username":"$username","password":"$PASSWORD","withToday":true}""")
        step("start", "GET", "/api/start?withToday=true")
        step("today-screen", "GET", "/api/branches/$branchId/today-screen")
        step(
            "clock-in",
            "POST",
            "/api/attendance/clock-in?withToday=true",
            """{"attendanceId":"$attendance","branchId":"$branchId"}""",
        )
        step(
            "save session",
            "POST",
            "/api/sessions?withToday=true",
            """{"id":"${UUID.randomUUID()}","clientId":"$clientId","branchId":"$branchId",""" +
                """"isWalkIn":false,"finalPrice":"2500.00"}""",
        )
        step("clock-out", "POST", "/api/attendance/clock-out?withToday=true", """{"attendanceId":"$attendance"}""")

        println("route statements: $seen")
        val over = seen.filter { (name, n) -> n > CEILINGS.getValue(name) }
        assertEquals(
            emptyMap(),
            over,
            "statements per route ${seen.map { (k, n) -> "$k=$n (ceiling ${CEILINGS[k]})" }}",
        )
    }

    private fun call(
        method: String,
        path: String,
        body: String?,
    ): Int {
        val request =
            HttpRequest
                .newBuilder(URI(testServer.client.origin + path))
                .header("Content-Type", "application/json")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
                ).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (path == "/auth/login" && response.statusCode() == 200) {
            token =
                Json
                    .parseToJsonElement(response.body())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content
        }
        if (response.statusCode() !in
            200..299
        ) {
            println("$method $path ${response.statusCode()} ${response.body().take(300)}")
        }
        return response.statusCode()
    }

    companion object {
        private const val PASSWORD = "budget-pass-1"

        // Access reads one facts statement per request. Four routes also still read the old grant view for
        // /api/me/capabilities (the web's grants.ts): +1 each until the web gets its rights from the server.
        val CEILINGS =
            mapOf(
                "sign-in" to 23,
                "start" to 17,
                "today-screen" to 7,
                "clock-in" to 36,
                "save session" to 25,
                "clock-out" to 25,
            )

        private val http: HttpClient = HttpClient.newHttpClient()
        private val counts = ConcurrentHashMap<String, Int>()
        private var token: String? = null
        private var connected = false

        @get:ClassRule
        @JvmStatic
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before {
                    if (!connected) {
                        Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()))
                        connected = true
                    }
                    RequestQueryTally.startRequest()
                }
                configureAuth(cfg)
                registerExceptionHandlers(cfg)
                registerAllRoutes(cfg)
                cfg.routes.after { ctx ->
                    counts["${ctx.method()} ${ctx.path()}${ctx.queryString()?.let { "?$it" } ?: ""}"] =
                        RequestQueryTally.snapshot().queries
                    RequestQueryTally.endRequest()
                }
            }
        }
    }
}
