package com.companyb.companyapp.authorization

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchType
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
import io.javalin.testtools.Request
import io.javalin.testtools.Response
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.io.File
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Who can do what, as one table (owed #22). Each row is an action, on a day where the day
 * matters; each column is a kind of person. Y means the server did it (2xx), N means it refused
 * (403). Every request that should succeed carries a reason, so a cell never shows a missing-reason
 * 400. Any other code shows as itself.
 *
 * Columns. OWN PRA COO MAN SUP are people with that one role and a home branch here. ACC is an
 * Accountant, who never has a home branch. OWN* and SUP* are an Owner and a Superuser with no home
 * branch. REL is relief, granted today only. ONB is
 * someone still onboarding, assigned here. OUT is a practitioner from another branch.
 *
 * Everyone is clocked in here today, because adding a session today first needs a clock-in
 * ("Clock in at this branch first"), whoever you are; that rule is not about access.
 *
 * Rows. "today" is the open day, "earlier" a past day not remitted, "remitted" a day already sent.
 * A row named "... commission" or "... names Superuser" is Y only when the answer shows that thing:
 * commission is private to the branch's members (owed #26) and no sales report carries it; a
 * Superuser shows as one only to another Superuser (owed #24).
 */
class AccessTableTest : BasePostgresTest() {
    val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val missionId = TestFixtures.uuid()
    private val people = Who.entries.associateWith { TestFixtures.uuid() }
    private val payees = Who.entries.associateWith { TestFixtures.uuid() }
    private val soap = TestFixtures.uuid()
    private lateinit var days: Map<Day, UUID>

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)
        BranchWorkforceFixtures.insertTestBranch(missionId, branchType = BranchType.MEDICAL_MISSION)
        days =
            mapOf(
                Day.TODAY to BranchWorkforceFixtures.createBranchDayForDate(branchId, Day.TODAY.date()),
                Day.EARLIER to BranchWorkforceFixtures.createBranchDayForDate(branchId, Day.EARLIER.date()),
                Day.REMITTED to BranchWorkforceFixtures.createRemittedBranchDay(branchId, Day.REMITTED.date()),
            )
        Who.entries.forEachIndexed { slot, who ->
            val id = person(who)
            IdentityFixtures.insertTestUser(id, who.name.lowercase())
            assignRole(id, who.role)
            val home =
                when (who) {
                    Who.ACCOUNTANT, Who.OWNER_AWAY, Who.SUPERUSER_AWAY, Who.RELIEF -> null
                    Who.OUTSIDER -> otherBranchId
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
        people.values.forEach { BranchWorkforceFixtures.insertTestAttendance(day(Day.TODAY), it) }
        payees.values.forEach { IdentityFixtures.insertTestUser(it, "payee") }
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, person(Who.COORDINATOR))
        IdentityFixtures.grantCapability(
            person(Who.RELIEF),
            CapabilityCodes.EDIT_BRANCH_DATA,
            CapabilityContextType.BRANCH_DAY,
            day(Day.TODAY),
            TestFixtures.uuid(),
        )
        val category = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(category)
        CommerceFinanceFixtures.insertTestProduct(
            soap,
            name = "Soap",
            categoryId = category,
            unitPrice = BigDecimal("100.00"),
        )
        CommerceFinanceFixtures.insertTestCard(branchId, soap, STOCK)
    }

    @Test
    fun `who can do what matches the table`() {
        val observed =
            ROWS.associate { row ->
                row.label to Who.entries.associateWith { who -> cell(row.action.call(this, who, row.day), row.shows) }
            }
        val expected = parse(EXPECTED)
        assertEquals(ROWS.map { it.label }, expected.keys.toList(), "every row of the table is checked, in order")
        val wrong =
            ROWS.flatMap { row ->
                Who.entries.mapNotNull { who ->
                    val want = expected[row.label]?.get(who)
                    val got = observed.getValue(row.label).getValue(who)
                    if (want == got) null else "${row.label} / ${who.column}: expected $want, got $got"
                }
            }
        assertEquals(
            emptyList(),
            wrong,
            "Observed table:\n${render(observed)}\nOdd:\n${odd.distinct().joinToString("\n")}\n",
        )
    }

    private val odd = mutableListOf<String>()

    private fun cell(
        r: Response,
        shows: ((String) -> Boolean)?,
    ): String =
        when (r.code) {
            in 200..299 -> if (shows == null || shows(r.body.string())) "Y" else "N"
            403 -> "N"
            else -> "${r.code}".also { odd += "${r.code} ${r.body.string().take(160)}" }
        }

    private fun person(who: Who) = people.getValue(who)

    fun day(d: Day) = days.getValue(d)

    private fun as_(who: Who) = Consumer<Request.Builder> { it.header("X-Test-User", person(who).toString()) }

    private fun get(
        path: String,
        who: Who,
    ) = testServer.client.get(path, as_(who))

    /**
     * GET an Excel export as the text of its parts. The test client reads bodies as text,
     * which garbles a workbook's zip, so this reads the bytes and unzips them first.
     */
    private fun getWorkbook(
        path: String,
        who: Who,
    ): Response {
        val request =
            HttpRequest
                .newBuilder(URI(testServer.client.origin + path + "&format=xlsx"))
                .header("X-Test-User", person(who).toString())
                .GET()
                .build()
        val response =
            HttpClient.newHttpClient().send(request) { info ->
                HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray()) { bytes ->
                    if (info.statusCode() == 200) unzipText(bytes) else String(bytes)
                }
            }
        return Response(response)
    }

    private fun unzipText(bytes: ByteArray): String {
        val file = File.createTempFile("export", ".xlsx").apply { deleteOnExit() }
        file.writeBytes(bytes)
        return ZipFile(file).use { zip ->
            zip.entries().asSequence().joinToString("\n") { String(zip.getInputStream(it).readBytes()) }
        }
    }

    private fun post(
        path: String,
        body: Any,
        who: Who,
    ) = testServer.client.post(path, body, as_(who))

    private fun patch(
        path: String,
        body: Any,
        who: Who,
    ) = testServer.client.patch(path, body, as_(who))

    private fun newSession(d: Day): UUID {
        val client = TestFixtures.uuid()
        val id = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(client)
        SessionClientFixtures.insertTestSession(id, client, day(d))
        return id
    }

    private fun addSession(
        who: Who,
        d: Day,
    ): Response {
        val client = TestFixtures.uuid().also { SessionClientFixtures.insertTestClient(it) }
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "clientId" to client.toString(),
                "branchId" to branchId.toString(),
                "isWalkIn" to false,
                "finalPrice" to "2500.00",
                "date" to d.date().toString(),
                "reason" to REASON,
            )
        return post(ApiRoutes.SESSIONS, body, who)
    }

    private fun newSale(d: Day): UUID {
        val id = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(id, day(d), soap, person(Who.PRACTITIONER))
        return id
    }

    private fun newExpense(d: Day): UUID =
        CommerceFinanceFixtures.insertTestExpense(day(d), person(Who.PRACTITIONER), BigDecimal("50.00"))

    private fun expenseBody(d: Day) =
        mapOf(
            "id" to TestFixtures.uuid().toString(),
            "branchDayId" to day(d).toString(),
            "amount" to "50.00",
            "category" to "PANTRY",
            "reason" to REASON,
        )

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId = transaction { RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id] }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    enum class Who(
        val role: String,
        val column: String,
    ) {
        OWNER("OWNER", "OWN"),
        ACCOUNTANT("ACCOUNTANT", "ACC"),
        PRACTITIONER("PRACTITIONER", "PRA"),
        COORDINATOR("COORDINATOR", "COO"),
        MANAGER("MANAGER", "MAN"),
        SUPERUSER("SUPERUSER", "SUP"),
        OWNER_AWAY("OWNER", "OWN*"),
        SUPERUSER_AWAY("SUPERUSER", "SUP*"),
        RELIEF("PRACTITIONER", "REL"),
        ONBOARDING("ONBOARDING", "ONB"),
        OUTSIDER("PRACTITIONER", "OUT"),
    }

    enum class Day(
        private val daysAgo: Long,
    ) {
        TODAY(0),
        EARLIER(1),
        REMITTED(2),
        ;

        fun date(): LocalDate = TestFixtures.today.minusDays(daysAgo)
    }

    /** One thing a person tries. [day] is null when the day plays no part. */
    fun interface Action {
        fun call(
            t: AccessTableTest,
            who: Who,
            day: Day?,
        ): Response
    }

    /** [shows], when set, turns a 2xx into Y only if the body passes it, so a row can check what is shown. */
    class Row(
        val name: String,
        val day: Day?,
        val shows: ((String) -> Boolean)? = null,
        val action: Action,
    ) {
        val label = if (day == null) name else "$name ${day.name.lowercase()}"
    }

    companion object {
        private const val REASON = "table check"
        private const val STOCK = 1000

        private fun daily(
            name: String,
            action: AccessTableTest.(Who, Day) -> Response,
        ) = Day.entries.map { d -> Row(name, d) { t, who, _ -> t.action(who, d) } }

        private fun once(
            name: String,
            action: AccessTableTest.(Who) -> Response,
        ) = listOf(Row(name, null) { t, who, _ -> t.action(who) })

        private val ROWS: List<Row> =
            daily("session.add") { who, d -> addSession(who, d) } +
                daily("session.price") { who, d ->
                    patch(
                        "${ApiRoutes.SESSIONS}/${newSession(d)}/final-price",
                        mapOf("finalPrice" to "1.00", "version" to 1, "reason" to REASON),
                        who,
                    )
                } +
                daily("session.mark") { who, d ->
                    patch(
                        "${ApiRoutes.SESSIONS}/${newSession(d)}/status",
                        mapOf("status" to "NO_SHOW", "version" to 1, "reason" to REASON),
                        who,
                    )
                } +
                daily("session.void") { who, d ->
                    post(
                        "${ApiRoutes.SESSIONS}/${newSession(d)}/void",
                        mapOf("id" to TestFixtures.uuid().toString(), "voidReason" to REASON),
                        who,
                    )
                } +
                daily("sale.add") { who, d ->
                    post(
                        ApiRoutes.PRODUCT_SALES,
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "branchDayId" to day(d).toString(),
                            "isWalkIn" to true,
                            "productId" to soap.toString(),
                            "quantity" to 1,
                            "reason" to REASON,
                        ),
                        who,
                    )
                } +
                daily("sale.edit") { who, d ->
                    patch(
                        "${ApiRoutes.PRODUCT_SALES}/${newSale(d)}",
                        mapOf(
                            "quantity" to 2,
                            "productId" to soap.toString(),
                            "expectedVersion" to 1,
                            "reason" to REASON,
                        ),
                        who,
                    )
                } +
                daily("sale.list") { who, d -> get("${ApiRoutes.PRODUCT_SALES}?branchDayId=${day(d)}", who) } +
                daily("expense.add") { who, d -> post(ApiRoutes.EXPENSES, expenseBody(d), who) } +
                daily("expense.edit") { who, d ->
                    patch(
                        "${ApiRoutes.EXPENSES}/${newExpense(d)}",
                        mapOf("amount" to "60.00", "category" to "PANTRY", "expectedVersion" to 1, "reason" to REASON),
                        who,
                    )
                } +
                daily("expense.list") { who, d -> get("${ApiRoutes.EXPENSES}?branchDayId=${day(d)}", who) } +
                daily("pay.add") { who, d ->
                    post(
                        ApiRoutes.COMPENSATION,
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "workBranchDayId" to day(d).toString(),
                            "payingBranchDayId" to day(d).toString(),
                            "userId" to payees.getValue(who).toString(),
                            "amount" to "100.00",
                            "reason" to REASON,
                        ),
                        who,
                    )
                } +
                daily("pay.list") { who, d -> get("${ApiRoutes.COMPENSATIONS}?branchDayId=${day(d)}", who) } +
                daily(
                    "commission.recalc",
                ) { who, d -> post("${ApiRoutes.COMMISSION_RECALCULATE}/${day(d)}", emptyMap<String, String>(), who) } +
                daily("commission.list") { who, d -> get("${ApiRoutes.COMMISSION_SPLITS}/${day(d)}", who) } +
                daily("stock.restock") { who, d ->
                    post(
                        "${ApiRoutes.BRANCHES}/$branchId/inventory/$soap/restock",
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "quantity" to 1,
                            "branchDayId" to day(d).toString(),
                            "editReason" to REASON,
                        ),
                        who,
                    )
                } +
                once(
                    "day.sessions",
                ) { who -> get("${ApiRoutes.BRANCHES}/$branchId/sessions?date=${TestFixtures.today}", who) } +
                once("day.today") { who -> get("${ApiRoutes.BRANCHES}/$branchId/today-screen", who) } +
                once("report.month") { who ->
                    val month = TestFixtures.currentMonth
                    get(
                        "${ApiRoutes.BRANCHES}/$branchId/monthly-summary?year=${month.year}&month=${month.monthValue}",
                        who,
                    )
                } +
                once("remittance.draft") { who ->
                    val date = TestFixtures.today.minusDays(10L + who.ordinal).toString()
                    post(
                        ApiRoutes.REMITTANCES,
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "type" to "SESSION",
                            "branchId" to branchId.toString(),
                            "method" to "BANK_TRANSFER",
                            "dateRangeStart" to date,
                            "dateRangeEnd" to date,
                        ),
                        who,
                    )
                } +
                once("client.search") { who -> get("${ApiRoutes.CLIENTS}?q=a", who) } +
                once("stock.list") { who -> get("${ApiRoutes.BRANCHES}/$branchId/inventory", who) } +
                once("rates.list") { who -> get("${ApiRoutes.BRANCHES}/$branchId/rates", who) } +
                once("catalog.add") { who ->
                    post(
                        ApiRoutes.PRODUCT_CATEGORIES,
                        mapOf("id" to TestFixtures.uuid().toString(), "name" to "Cat ${TestFixtures.uuid()}"),
                        who,
                    )
                } +
                listOf(
                    Row("sale.list commission", null, { it.contains("\"commissionAmountAtTime\":\"") }) { t, who, _ ->
                        t.get("${ApiRoutes.PRODUCT_SALES}?branchDayId=${t.day(Day.TODAY)}", who)
                    },
                    Row("summary commission", null, { it.contains("\"totalCommission\":") }) { t, who, _ ->
                        t.get("${ApiRoutes.BRANCHES}/${t.branchId}/daily-summary?date=${TestFixtures.today}", who)
                    },
                    Row("report.export commission", null, { it.contains("Commission") }) { t, who, _ ->
                        val from = TestFixtures.today.minusDays(3)
                        t.getWorkbook(
                            "${ApiRoutes.BRANCHES}/${t.branchId}/export/report?from=$from&to=${TestFixtures.today}",
                            who,
                        )
                    },
                ) +
                once("users.list") { who -> get(ApiRoutes.USERS, who) } +
                listOf(
                    Row(
                        "users.list names Superuser",
                        null,
                        { it.contains("\"SUPERUSER\"") },
                    ) { t, who, _ -> t.get(ApiRoutes.USERS, who) },
                ) +
                once("delegates.list") { who -> get("${ApiRoutes.BRANCHES}/$missionId/delegates", who) } +
                once("view-as") { who ->
                    testServer.client.get(ApiRoutes.ME) {
                        as_(who).accept(it)
                        it.header(ViewAs.HEADER, person(Who.PRACTITIONER).toString())
                    }
                }

        private val EXPECTED =
            """
                                         OWN  ACC  PRA  COO  MAN  SUP OWN* SUP*  REL  ONB  OUT
            session.add today              Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            session.add earlier            Y    N    Y    Y    Y    Y    N    Y    N    N    N
            session.add remitted           N    N    N    Y    Y    Y    N    Y    N    N    N
            session.price today            Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            session.price earlier          Y    N    Y    Y    Y    Y    N    Y    N    N    N
            session.price remitted         N    N    N    Y    Y    Y    N    Y    N    N    N
            session.mark today             Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            session.mark earlier           Y    N    Y    Y    Y    Y    N    Y    N    N    N
            session.mark remitted          N    N    N    Y    Y    Y    N    Y    N    N    N
            session.void today             Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            session.void earlier           Y    N    Y    Y    Y    Y    N    Y    N    N    N
            session.void remitted          N    N    N    Y    Y    Y    N    Y    N    N    N
            sale.add today                 Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            sale.add earlier               Y    N    Y    Y    Y    Y    N    Y    N    N    N
            sale.add remitted              N    N    N    Y    Y    Y    N    Y    N    N    N
            sale.edit today                Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            sale.edit earlier              Y    N    Y    Y    Y    Y    N    Y    N    N    N
            sale.edit remitted             N    N    N    Y    Y    Y    N    Y    N    N    N
            sale.list today                Y    Y    Y    Y    Y    Y    Y    Y    Y    N    N
            sale.list earlier              Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            sale.list remitted             Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            expense.add today              Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            expense.add earlier            Y    N    Y    Y    Y    Y    N    Y    N    N    N
            expense.add remitted           N    N    N    Y    Y    Y    N    Y    N    N    N
            expense.edit today             Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            expense.edit earlier           Y    N    Y    Y    Y    Y    N    Y    N    N    N
            expense.edit remitted          N    N    N    Y    Y    Y    N    Y    N    N    N
            expense.list today             Y    Y    Y    Y    Y    Y    Y    Y    Y    N    N
            expense.list earlier           Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            expense.list remitted          Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            pay.add today                  Y    N    N    Y    Y    Y    Y    Y    N    N    N
            pay.add earlier                N    N    N    Y    Y    Y    N    Y    N    N    N
            pay.add remitted               N    N    N    Y    Y    Y    N    Y    N    N    N
            pay.list today                 Y    N    N    Y    Y    Y    Y    Y    N    N    N
            pay.list earlier               Y    N    N    Y    Y    Y    Y    Y    N    N    N
            pay.list remitted              Y    N    N    Y    Y    Y    Y    Y    N    N    N
            commission.recalc today        N    N    N    Y    Y    Y    N    Y    N    N    N
            commission.recalc earlier      N    N    N    Y    Y    Y    N    Y    N    N    N
            commission.recalc remitted     N    N    N    Y    Y    Y    N    Y    N    N    N
            commission.list today          Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            commission.list earlier        Y    N    Y    Y    Y    Y    N    Y    N    N    N
            commission.list remitted       Y    N    Y    Y    Y    Y    N    Y    N    N    N
            stock.restock today            Y    N    N    Y    Y    Y    N    Y    N    N    N
            stock.restock earlier          N    N    N    Y    Y    Y    N    Y    N    N    N
            stock.restock remitted         N    N    N    Y    Y    Y    N    Y    N    N    N
            day.sessions                   Y    Y    Y    Y    Y    Y    Y    Y    Y    N    N
            day.today                      Y    Y    Y    Y    Y    Y    Y    Y    Y    Y    Y
            report.month                   Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            remittance.draft               N    N    N    Y    Y    Y    N    Y    N    N    N
            client.search                  Y    Y    Y    Y    Y    Y    Y    Y    Y    N    Y
            stock.list                     Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            rates.list                     Y    Y    Y    Y    Y    Y    Y    Y    N    N    N
            catalog.add                    Y    N    Y    Y    Y    Y    Y    Y    Y    N    Y
            sale.list commission           Y    N    Y    Y    Y    Y    N    Y    Y    N    N
            summary commission             N    N    N    N    N    N    N    N    N    N    N
            report.export commission       N    N    N    N    N    N    N    N    N    N    N
            users.list                     Y    N    N    N    Y    Y    Y    Y    N    N    N
            users.list names Superuser     N    N    N    N    N    Y    N    Y    N    N    N
            delegates.list                 Y    N    N    N    Y    Y    Y    Y    N    N    N
            view-as                        N    N    N    N    N    Y    N    Y    N    N    N
            """.trimIndent()

        private fun parse(table: String): Map<String, Map<Who, String>> =
            table.lines().drop(1).filter { it.isNotBlank() }.associate { line ->
                val label = line.substring(0, LABEL_WIDTH).trim()
                val cells = line.substring(LABEL_WIDTH).trim().split(Regex("\\s+"))
                label to Who.entries.zip(cells).toMap()
            }

        private fun render(observed: Map<String, Map<Who, String>>): String {
            val head = "".padEnd(LABEL_WIDTH) + Who.entries.joinToString(" ") { it.column.padStart(COLUMN_WIDTH) }
            val body =
                observed.map { (label, cells) ->
                    label.padEnd(LABEL_WIDTH) +
                        Who.entries.joinToString(" ") { cells.getValue(it).padStart(COLUMN_WIDTH) }
                }
            return (listOf(head) + body).joinToString("\n")
        }

        private const val LABEL_WIDTH = 28
        private const val COLUMN_WIDTH = 4
        private val defaultUser = TestFixtures.uuid()

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
