package com.companyb.companyapp.authorization

import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.GatedHandler
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.identity.ViewAs
import com.companyb.companyapp.registerAllRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.http.HandlerType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The status code every route gives each kind of person, for an empty `{}` body, and the gate the route
 * declares (`api/Gate.kt`), as one file: [FILE]. It pins who gets past each route's gate, so a
 * change to who can do what on any route shows up as a diff of that file. Rewrite it with
 * `WRITE_ROUTE_ACCESS=1 ./gradlew :backend:test --tests '*RouteAccessTest'` and read the diff.
 *
 * Path parameters name real rows where the name says which: a branch, today's day there, a
 * practitioner. Every other id is one that does not exist. `AccessTableTest` checks real actions
 * with valid bodies; this checks every route, so a gate that runs after body checks shows as 400.
 */
class RouteAccessTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val missing = TestFixtures.uuid()
    private val people = Who.entries.associateWith { TestFixtures.uuid() }
    private lateinit var today: UUID

    enum class Who(
        val role: String?,
    ) {
        OWN("OWNER"),
        ACC("ACCOUNTANT"),
        PRA("PRACTITIONER"),
        COO("COORDINATOR"),
        MAN("MANAGER"),
        SUP("SUPERUSER"),
        OWNx("OWNER"),
        OUT("PRACTITIONER"),
        REL("PRACTITIONER"),
        NON(null),
    }

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)
        today = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today)
        Who.entries.forEachIndexed { slot, who ->
            val id = people.getValue(who)
            IdentityFixtures.insertTestUser(id, who.name.lowercase())
            who.role?.let { assignRole(id, it) }
            val home =
                when (who) {
                    Who.ACC, Who.OWNx, Who.REL, Who.NON -> null
                    Who.OUT -> otherBranchId
                    else -> branchId
                }
            home?.let {
                BranchWorkforceFixtures.insertTestAssignment(
                    userId = id,
                    branchId = it,
                    slot = (slot + 1).toShort(),
                    assignedBy = id,
                )
            }
        }
        IdentityFixtures.reliefDay(
            userId = people.getValue(Who.REL),
            branchDayId = today,
            sourceId = TestFixtures.uuid(),
        )
    }

    @Test
    fun `every route gives each person the code in the file`() {
        val observed = render(routes.map { r -> r to Who.entries.map { call(r.method, r.path, it) } })
        val file = File(FILE)
        if (System.getenv("WRITE_ROUTE_ACCESS") == "1") file.writeText(observed)
        assertEquals(
            file.readText(),
            observed,
            "$FILE is stale: run WRITE_ROUTE_ACCESS=1 ./gradlew :backend:test --tests '*RouteAccessTest' " +
                "and read the diff",
        )
    }

    private fun call(
        method: HandlerType,
        path: String,
        who: Who,
    ): Int {
        val url = testServer.client.origin + fill(path)
        val body =
            if (method ==
                HandlerType.GET
            ) {
                HttpRequest.BodyPublishers.noBody()
            } else {
                HttpRequest.BodyPublishers.ofString("{}")
            }
        val request =
            HttpRequest
                .newBuilder(URI(url))
                .header("X-Test-User", people.getValue(who).toString())
                .header("Content-Type", "application/json")
                .method(method.name, body)
                .build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun fill(path: String): String =
        PARAM.replace(path) { m ->
            when (m.groupValues[1]) {
                "branchId" -> branchId
                "branchDayId", "dayId" -> today
                "userId" -> people.getValue(Who.PRA)
                else -> missing
            }.toString()
        }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        transaction {
            val roleId = RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    companion object {
        const val FILE = "backend/src/test/resources/route-access.txt"
        private val PARAM = Regex("\\{(\\w+)}")
        private val http: HttpClient = HttpClient.newHttpClient()

        private class Route(
            val method: HandlerType,
            val path: String,
            val gate: String,
        )

        /** Every HTTP route the app registers, in registration order. */
        private val routes = mutableListOf<Route>()

        private fun label(gate: Gate?): String =
            when (gate) {
                is Gate.Open -> "Open: ${gate.why}"
                is Gate.SignedIn -> "SignedIn: ${gate.why}"
                is Gate.InHandler -> "InHandler: ${gate.why}"
                is Gate.Before -> "Before"
                null -> "none"
            }

        private fun render(rows: List<Pair<Route, List<Int>>>): String {
            val width = rows.maxOf { "${it.first.method} ${it.first.path}".length }
            val head = "route".padEnd(width) + Who.entries.joinToString("") { " " + it.name.padStart(4) } + "  gate"
            val lines =
                rows.map { (r, codes) ->
                    "${r.method} ${r.path}".padEnd(width) + codes.joinToString("") { " " + "$it".padStart(4) } + "  " +
                        r.gate
                }
            return (listOf(head) + lines).joinToString("\n", postfix = "\n")
        }

        private val defaultUser = TestFixtures.uuid()

        @get:ClassRule
        @JvmStatic
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            routes.clear()
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.events.handlerAdded { meta ->
                    if (meta.httpMethod.isHttpMethod) {
                        routes +=
                            Route(meta.httpMethod, meta.path, label((meta.handler as? GatedHandler)?.gate))
                    }
                }
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ViewAs.resolve(ctx, ctx.header("X-Test-User") ?: defaultUser.toString()))
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(
                        mapOf("error" to e.message),
                    )
                }
                registerAllRoutes(cfg)
            }
        }
    }
}
