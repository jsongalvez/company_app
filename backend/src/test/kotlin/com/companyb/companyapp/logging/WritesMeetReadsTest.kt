package com.companyb.companyapp.logging

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.database.JdbcTiming
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
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.http.HandlerType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The web marks a cached answer stale when a save's `X-Changed` meets the answer's `X-Reads`
 * (`docs/rebuild/plan.md`, Revisions 2 and 3, check b). So for each scripted save, every GET route is
 * read before and after it on the same seed, and every answer that changed must have read a table
 * the save changed. A miss here is a screen the web would leave stale: a read [StatementTables]
 * didn't name, or a write it didn't name.
 */
class WritesMeetReadsTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val missionId = TestFixtures.uuid()
    private val missing = TestFixtures.uuid()
    private val people = Who.entries.associateWith { TestFixtures.uuid() }
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val attendanceId = TestFixtures.uuid()
    private val remittanceId = TestFixtures.uuid()
    private lateinit var today: UUID
    private lateinit var earlier: UUID

    enum class Who(
        val role: String,
    ) {
        SUP("SUPERUSER"),
        PRA("PRACTITIONER"),
        OUT("PRACTITIONER"),
        NEW("PRACTITIONER"),
    }

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)
        BranchWorkforceFixtures.insertTestBranch(missionId, branchType = BranchType.MEDICAL_MISSION)
        today = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today)
        earlier = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(EARLIER))
        Who.entries.forEachIndexed { slot, who ->
            val id = person(who)
            IdentityFixtures.insertTestUser(id, who.name.lowercase())
            assignRole(id, who.role)
            val home =
                when (who) {
                    Who.OUT -> otherBranchId
                    Who.NEW -> null
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
        BranchWorkforceFixtures.insertTestAttendance(today, person(Who.PRA))
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, person(Who.SUP))
        SessionClientFixtures.insertTestClient(clientId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, name = "Soap", categoryId = categoryId)
        CommerceFinanceFixtures.insertTestCard(branchId, productId, STOCK)
    }

    @Test
    fun `every answer a save changes read a table the save changed`() {
        val first = readAll()
        val volatile = first.keys.filter { first[it]?.body != readAll()[it]?.body }.toSet()
        assertEquals(VOLATILE.keys, volatile.map { it.substringBefore(" as ") }.toSet(), "answers that change unread")
        var before = first
        val problems =
            saves().flatMap { save ->
                val response = send(save.method, save.path, Who.SUP, save.body)
                val changed = response.tables(RequestLog.CHANGED_HEADER)
                val after = readAll()
                val moved = after.filter { (read, answer) -> read !in volatile && before[read]?.body != answer.body }
                val misses =
                    moved.mapNotNull { (read, answer) ->
                        val met =
                            STAR in changed || STAR in answer.reads || (answer.reads intersect changed).isNotEmpty()
                        "${save.name} changed $changed; $read read ${answer.reads}".takeUnless { met }
                    }
                before = after
                val refused =
                    "${save.name}: ${response.statusCode()} ${response.body().take(BODY_SHOWN)}"
                        .takeIf { response.statusCode() !in OK }
                val unseen = "${save.name} changed no answer, so it checks nothing".takeIf { moved.isEmpty() }
                listOfNotNull(refused, unseen) + misses
            }
        assertEquals(emptyList(), problems)
    }

    private class Save(
        val name: String,
        val method: HandlerType,
        val path: String,
        val body: String,
    )

    private class Answer(
        val body: String,
        val reads: Set<String>,
    )

    private fun saves(): List<Save> = visitSaves() + branchSaves()

    private fun visitSaves(): List<Save> {
        val day = TestFixtures.today
        return listOf(
            Save(
                "clock-in",
                HandlerType.POST,
                "/api/attendance/clock-in",
                """{"attendanceId":"$attendanceId","branchId":"$branchId"}""",
            ),
            Save(
                "client add",
                HandlerType.POST,
                "/api/clients",
                """{"id":"${TestFixtures.uuid()}","firstName":"Ana","lastName":"Reyes","gender":"F","age":40}""",
            ),
            Save("client edit", HandlerType.PATCH, "/api/clients/$clientId", """{"phoneNumber":"09171234567"}"""),
            Save(
                "booking add",
                HandlerType.POST,
                "/api/branches/$branchId/bookings",
                """{"id":"${TestFixtures.uuid()}","clientId":"$clientId","visitDate":"${day.plusDays(1)}"}""",
            ),
            Save(
                "session add",
                HandlerType.POST,
                "/api/sessions",
                """{"id":"$sessionId","clientId":"$clientId","branchId":"$branchId","isWalkIn":false,""" +
                    """"finalPrice":"2500.00","date":"$day",$REASON}""",
            ),
            Save(
                "session complete",
                HandlerType.PATCH,
                "/api/sessions/$sessionId/status",
                """{"status":"COMPLETED","version":1,$REASON}""",
            ),
        )
    }

    private fun branchSaves(): List<Save> {
        val day = TestFixtures.today
        return listOf(
            Save(
                "product sale",
                HandlerType.POST,
                "/api/product-sales",
                """{"id":"${TestFixtures.uuid()}","branchDayId":"$today","isWalkIn":true,""" +
                    """"productId":"$productId","quantity":1,$REASON}""",
            ),
            Save(
                "restock",
                HandlerType.POST,
                "/api/branches/$branchId/inventory/$productId/restock",
                """{"id":"${TestFixtures.uuid()}","quantity":3,"branchDayId":"$today","editReason":"check b"}""",
            ),
            Save(
                "expense",
                HandlerType.POST,
                "/api/expenses",
                """{"id":"${TestFixtures.uuid()}","branchDayId":"$today","amount":"50.00",""" +
                    """"category":"PANTRY",$REASON}""",
            ),
            Save(
                "pay",
                HandlerType.POST,
                "/api/compensation",
                """{"id":"${TestFixtures.uuid()}","workBranchDayId":"$today","payingBranchDayId":"$today",""" +
                    """"userId":"${person(Who.PRA)}","amount":"100.00",$REASON}""",
            ),
            Save(
                "relief invite",
                HandlerType.POST,
                "/api/branches/$branchId/relief-invites",
                """{"inviteeUserId":"${person(Who.OUT)}","date":"$day"}""",
            ),
            Save(
                "helper add",
                HandlerType.POST,
                "/api/delegates",
                """{"delegateId":"${TestFixtures.uuid()}","targetUserId":"${person(
                    Who.NEW,
                )}","branchId":"$missionId"}""",
            ),
            Save(
                "remittance draft",
                HandlerType.POST,
                "/api/remittances",
                """{"id":"$remittanceId","type":"SESSION","branchId":"$branchId","method":"BANK_TRANSFER",""" +
                    """"dateRangeStart":"${day.minusDays(EARLIER)}","dateRangeEnd":"${day.minusDays(EARLIER)}"}""",
            ),
            Save("clock-out", HandlerType.POST, "/api/attendance/clock-out", """{"attendanceId":"$attendanceId"}"""),
        )
    }

    /**
     * Every GET route as each reader, with [filledQuery] so handlers that need parameters answer, plus the
     * delegates of [missionId]: a helper add writes there, and [fill] points `{branchId}` at [branchId].
     */
    private fun readAll(): Map<String, Answer> =
        (routes + ApiRoutes.BRANCH_DELEGATES_PATH.replace("{branchId}", missionId.toString()))
            .flatMap { path ->
                READERS.map { who ->
                    val response = send(HandlerType.GET, path + filledQuery(), who, "")
                    "GET $path as $who" to
                        Answer("${response.statusCode()} ${response.body()}", response.tables(RequestLog.READS_HEADER))
                }
            }.toMap()

    private fun HttpResponse<String>.tables(header: String): Set<String> =
        headers()
            .firstValue(header)
            .map { value -> value.split(",").filter { it.isNotEmpty() }.toSet() }
            .orElse(setOf(STAR))

    private fun filledQuery(): String {
        val day = TestFixtures.today
        return listOf(
            "q" to "client",
            "date" to day,
            "from" to day.minusDays(WEEK),
            "to" to day,
            "dateFrom" to day.minusDays(WEEK),
            // Audit ends dateTo at calendar midnight; before the 04:00 cutoff the operational day is yesterday.
            "dateTo" to maxOf(day, LocalDate.now(BranchDayService.manilaZone)),
            "branchId" to branchId,
            "branchDayId" to today,
            "withToday" to true,
            "type" to "SESSION",
            "limit" to LIMIT,
        ).joinToString("&", prefix = "?") { (k, v) -> "$k=$v" }
    }

    private fun send(
        method: HandlerType,
        path: String,
        who: Who,
        body: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI(testServer.client.origin + fill(path)))
                .header("X-Test-User", person(who).toString())
                .header("Content-Type", "application/json")
                .method(
                    method.name,
                    if (method ==
                        HandlerType.GET
                    ) {
                        HttpRequest.BodyPublishers.noBody()
                    } else {
                        HttpRequest.BodyPublishers.ofString(body)
                    },
                ).build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun fill(path: String): String =
        PARAM.replace(path) { m ->
            when (m.groupValues[1]) {
                "branchId" -> branchId
                "branchDayId", "dayId" -> today
                "userId" -> person(Who.PRA)
                "clientId" -> clientId
                "sessionId" -> sessionId
                "productId" -> productId
                "categoryId" -> categoryId
                "remittanceId" -> remittanceId
                else -> missing
            }.toString()
        }

    private fun person(who: Who) = people.getValue(who)

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
        private const val STAR = "*"
        private const val REASON = "\"reason\":\"check b\""
        private const val STOCK = 100
        private const val EARLIER = 3L
        private const val WEEK = 7L
        private const val LIMIT = 20
        private const val BODY_SHOWN = 200
        private val OK = 200..299
        private val READERS = listOf(Who.SUP, Who.PRA)
        private val PARAM = Regex("\\{(\\w+)}")
        private val http: HttpClient = HttpClient.newHttpClient()

        /** GET routes whose answer changes between two reads with no save between, and why. */
        private val VOLATILE: Map<String, String> = emptyMap()

        /** Every GET route the app registers. */
        private val routes = mutableListOf<String>()
        private val defaultUser = TestFixtures.uuid()

        /** Statements reach the tally only through [JdbcTiming]; one connection serves every request. */
        private val timed by lazy { Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource())) }

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
                    if (meta.httpMethod == HandlerType.GET) routes += meta.path
                }
                cfg.routes.before { ctx ->
                    TransactionManager.defaultDatabase = timed
                    RequestQueryTally.startRequest()
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
                cfg.routes.after { ctx ->
                    RequestLog.sendTables(ctx)
                    RequestQueryTally.endRequest()
                }
            }
        }
    }
}
