package com.companyb.companyapp.integration

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.OpLog
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.branchday.OpTime
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.configureAuth
import com.companyb.companyapp.configureHttpSurface
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.registerAllRoutes
import com.companyb.companyapp.registerExceptionHandlers
import com.companyb.companyapp.registerServerErrorHandler
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import io.javalin.Javalin
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Several phones come back into signal at once and send their queues together, through the real
 * HTTP stack (sign-in, `X-Op-Id`, `X-Occurred-At`, every route) against Postgres. Threads start
 * on one latch so the first requests of each phone meet in the database.
 */
class ConcurrentReplayPostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val phones = List(PHONES) { TestFixtures.uuid() }
    private val today: LocalDate = BranchDayService.currentOperationalDate()
    private val yesterday: LocalDate = today.minusDays(1)
    private val app: Javalin = createApp().start(0)
    private val http: HttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    private val pool = Executors.newFixedThreadPool(THREADS)

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId, "Concurrent Replay Branch")
        val categoryId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(categoryId, "Concurrent Replay Category")
        CommerceFinanceFixtures.insertTestProduct(productId, "Concurrent Replay Product", categoryId)
        CommerceFinanceFixtures.insertTestCard(branchId, productId, STOCK)
        phones.forEachIndexed { i, phone ->
            IdentityFixtures.insertTestUser(phone, "phone$i")
            IdentityFixtures.member(phone, branchId, if (coordinator(i)) Role.COORDINATOR else Role.PRACTITIONER)
        }
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, phones.first())
    }

    @AfterTest
    fun stop() {
        pool.shutdownNow()
        app.stop()
    }

    private fun coordinator(i: Int) = i % 2 == 0

    private class Op(
        val user: UUID,
        val method: String,
        val path: String,
        val body: String = "",
        val opId: String? = UUID.randomUUID().toString(),
        val occurred: Instant? = null,
    )

    private class Answer(
        val code: Int,
        val body: String,
    )

    private fun send(op: Op): Answer {
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:${app.port()}${op.path}"))
                .header("Authorization", "Bearer ${JwtService.generateToken(op.user.toString())}")
                .header("Content-Type", "application/json")
                .apply { op.opId?.let { header(OpLog.HEADER, it) } }
                .apply { op.occurred?.let { header(OpTime.HEADER, it.toString()) } }
                .method(
                    op.method,
                    if (op.method == "GET") {
                        HttpRequest.BodyPublishers.noBody()
                    } else {
                        HttpRequest.BodyPublishers.ofString(op.body)
                    },
                ).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return Answer(response.statusCode(), response.body())
    }

    /** What the phone's queue does: a save still running elsewhere is sent again a moment later. */
    private fun sendUntilAnswered(op: Op): Answer {
        repeat(BUSY_RETRIES) {
            val answer = send(op)
            if (answer.code != BUSY) return answer
            Thread.sleep(BUSY_WAIT_MS)
        }
        error("${op.method} ${op.path} still busy after $BUSY_RETRIES tries")
    }

    /** Runs each task on its own thread, all released by one latch, and returns their results in order. */
    private fun <T> together(tasks: List<() -> T>): List<T> {
        val ready = CountDownLatch(tasks.size)
        val go = CountDownLatch(1)
        val futures =
            tasks.map { task ->
                pool.submit<T> {
                    ready.countDown()
                    go.await()
                    task()
                }
            }
        assertTrue(ready.await(WAIT_S, TimeUnit.SECONDS), "threads started")
        go.countDown()
        return futures.map { it.get(WAIT_S, TimeUnit.SECONDS) }
    }

    private fun reset() {
        TestDatabaseLifecycle.resetWorkerSchema()
        initTestData()
    }

    private fun at(
        day: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): Instant = day.atTime(hour, minute).atZone(BranchDayService.manilaZone).toInstant()

    private fun clockIn(
        user: UUID,
        attendanceId: UUID,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/attendance/clock-in",
        """{"attendanceId":"$attendanceId","branchId":"$branchId"}""",
        occurred = occurred,
    )

    private fun clockOut(
        user: UUID,
        attendanceId: UUID,
        occurred: Instant? = null,
    ) = Op(user, "POST", "/api/attendance/clock-out", """{"attendanceId":"$attendanceId"}""", occurred = occurred)

    private fun newClient(
        user: UUID,
        clientId: UUID,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/clients",
        """{"id":"$clientId","firstName":"Ana","lastName":"Reyes","gender":"F","age":40}""",
        occurred = occurred,
    )

    private fun newSession(
        user: UUID,
        sessionId: UUID,
        clientId: UUID,
        day: LocalDate,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/sessions",
        """{"id":"$sessionId","clientId":"$clientId","branchId":"$branchId","isWalkIn":false,""" +
            """"finalPrice":"$PRICE","date":"$day"}""",
        occurred = occurred,
    )

    private fun completeSession(
        user: UUID,
        sessionId: UUID,
        occurred: Instant? = null,
    ) = Op(
        user,
        "PATCH",
        "/api/sessions/$sessionId/status",
        """{"status":"COMPLETED","version":1}""",
        occurred = occurred,
    )

    private fun sale(
        user: UUID,
        saleId: UUID,
        dayId: UUID,
        quantity: Int,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/product-sales",
        """{"id":"$saleId","branchDayId":"$dayId","isWalkIn":true,"productId":"$productId","quantity":$quantity}""",
        occurred = occurred,
    )

    private fun restock(
        user: UUID,
        dayId: UUID,
        quantity: Int,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/branches/$branchId/inventory/$productId/restock",
        """{"id":"${TestFixtures.uuid()}","quantity":$quantity,"branchDayId":"$dayId","editReason":"delivery"}""",
        occurred = occurred,
    )

    private fun expense(
        user: UUID,
        expenseId: UUID,
        day: LocalDate,
        occurred: Instant? = null,
    ) = Op(
        user,
        "POST",
        "/api/expenses",
        """{"id":"$expenseId","branchId":"$branchId","date":"$day","amount":"$EXPENSE","category":"PANTRY"}""",
        occurred = occurred,
    )

    private fun todayScreen(user: UUID) = Op(user, "GET", "/api/branches/$branchId/today-screen", opId = null)

    private fun assertAnswered(
        answers: List<Answer>,
        what: String,
    ) {
        val bad = answers.filter { it.code !in OK }
        assertEquals(emptyList(), bad.map { "${it.code} ${it.body.take(BODY_SHOWN)}" }, what)
    }

    @Test
    fun `phones that open a fresh day together make one day and every save lands`() {
        repeat(ROUNDS) { round ->
            if (round > 0) reset()
            val sessions = phones.map { TestFixtures.uuid() }
            val answers =
                together(
                    phones.mapIndexed { i, phone ->
                        {
                            val client = TestFixtures.uuid()
                            listOf(
                                send(clockIn(phone, TestFixtures.uuid())),
                                send(
                                    if (coordinator(
                                            i,
                                        )
                                    ) {
                                        expense(phone, TestFixtures.uuid(), today)
                                    } else {
                                        todayScreen(phone)
                                    },
                                ),
                                send(newClient(phone, client)),
                                send(newSession(phone, sessions[i], client, today)),
                                send(todayScreen(phone)),
                            )
                        }
                    } + List(READERS) { i -> { listOf(send(todayScreen(phones[i]))) } },
                ).flatten()
            assertAnswered(answers, "round $round")
            transaction {
                assertEquals(
                    1,
                    BranchDayTable.selectAll().where { BranchDayTable.branchId eq branchId }.count(),
                    "one day",
                )
                assertEquals(PHONES.toLong(), AttendanceTable.selectAll().count(), "one shift each")
                assertEquals(
                    PHONES.toLong(),
                    SessionTable.selectAll().where { SessionTable.id inList sessions }.count(),
                )
            }
        }
    }

    @Test
    fun `one save sent four times at once is done once`() {
        repeat(ROUNDS) { round ->
            if (round > 0) reset()
            val (user, late) = phones[0] to phones[1]
            assertAnswered(listOf(send(clockIn(user, TestFixtures.uuid()))), "first shift")
            val dayId = BranchDayService.findByBranchAndDate(branchId, today)!!.id
            val client = TestFixtures.uuid()
            val session = TestFixtures.uuid()
            val saleId = TestFixtures.uuid()
            val expenseId = TestFixtures.uuid()
            val shift = TestFixtures.uuid()
            val ops =
                listOf(
                    newClient(user, client),
                    newSession(user, session, client, today),
                    sale(user, saleId, dayId, SOLD),
                    expense(user, expenseId, today),
                    restock(user, dayId, RESTOCKED),
                    clockIn(late, shift),
                )
            ops.forEach { op ->
                val raced = together(List(COPIES) { { send(op) } })
                val landed = raced.filter { it.code in OK }
                assertEquals(raced.size, landed.size + raced.count { it.code == BUSY }, "round $round ${op.path}")
                val settled = sendUntilAnswered(op)
                assertTrue(settled.code in OK, "round $round ${op.path}: ${settled.code} ${settled.body}")
                landed.forEach { assertEquals(settled.body, it.body, "every copy gets the same answer") }
            }
            transaction {
                assertEquals(1, ClientTable.selectAll().where { ClientTable.id eq client }.count())
                assertEquals(1, SessionTable.selectAll().where { SessionTable.id eq session }.count())
                assertEquals(1, ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count())
                assertEquals(1, ExpenseTable.selectAll().where { ExpenseTable.id eq expenseId }.count())
                assertEquals(1, AttendanceTable.selectAll().where { AttendanceTable.id eq shift }.count())
                assertEquals(2, InventoryMovementTable.selectAll().count(), "one sale and one restock move")
                assertEquals(STOCK - SOLD + RESTOCKED, stock())
            }
        }
    }

    @Test
    fun `late work from many phones lands once each, flagged, as if sent one at a time`() {
        repeat(ROUNDS) { round ->
            if (round > 0) reset()
            val dayId = TestFixtures.uuid()
            val queues = phones.mapIndexed { i, phone -> lateQueue(i, phone, dayId) }
            openYesterday(dayId)
            queues.flatten().forEach { assertTrue(sendUntilAnswered(it).code in OK) }
            val sequential = totals()

            reset()
            openYesterday(dayId)
            val answers = together(queues.map { queue -> { queue.map { sendUntilAnswered(it) } } }).flatten()
            assertAnswered(answers, "round $round")
            val concurrent = totals()
            assertEquals(sequential, concurrent, "round $round")
            val records = queues.flatten().mapNotNull { recordOf(it) }.toSet()
            transaction {
                val rows =
                    AuditLogTable
                        .selectAll()
                        .where { AuditLogTable.recordId inList records }
                        .map { it[AuditLogTable.recordId] to it[AuditLogTable.isFlagged] }
                assertEquals(records, rows.map { it.first }.toSet(), "every late save has history")
                assertTrue(rows.all { it.second }, "every late history row is flagged")
            }
        }
    }

    private fun openYesterday(dayId: UUID) {
        val branch = branchId
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = dayId
                it[BranchDayTable.branchId] = branch
                it[BranchDayTable.date] = yesterday
            }
        }
    }

    /** One phone's queue from yesterday, oldest first, each save sent twice as a lost answer would make it. */
    private fun lateQueue(
        i: Int,
        phone: UUID,
        dayId: UUID,
    ): List<Op> {
        val shift = TestFixtures.uuid()
        val client = TestFixtures.uuid()
        val session = TestFixtures.uuid()
        val saleId = TestFixtures.uuid()
        val expenseId = TestFixtures.uuid()
        val queue =
            listOfNotNull(
                clockIn(phone, shift, at(yesterday, LATE_START, i)),
                newClient(phone, client, at(yesterday, LATE_START + 1, i)),
                newSession(phone, session, client, yesterday, at(yesterday, LATE_START + 1, i + 1)),
                completeSession(phone, session, at(yesterday, LATE_START + 2, i)),
                sale(phone, saleId, dayId, i + 1, at(yesterday, LATE_START + 2, i + 1)),
                restock(phone, dayId, RESTOCKED, at(yesterday, LATE_START + 3, i)).takeIf { coordinator(i) },
                expense(phone, expenseId, yesterday, at(yesterday, LATE_START + 3, i + 1)),
                clockOut(phone, shift, at(yesterday, LATE_END, i)),
            )
        return queue.flatMapIndexed { n, op -> if ((n + i) % RESEND_EVERY == 0) listOf(op, op) else listOf(op) }
    }

    /** The day's record a save made; a new client belongs to no day, so it carries no late flag. */
    private fun recordOf(op: Op): UUID? =
        ID
            .find(op.body)
            ?.groupValues
            ?.get(1)
            ?.let(UUID::fromString)
            ?.takeIf { op.path.endsWith("clock-in") || op.path in RECORDED }

    private data class Totals(
        val days: List<LocalDate>,
        val shifts: Map<UUID, Pair<Instant, Instant?>>,
        val shiftDays: Set<UUID>,
        val clients: Long,
        val sessions: Map<UUID, String>,
        val sales: BigDecimal,
        val expenses: BigDecimal,
        val moves: Long,
        val stock: Int,
    )

    private fun totals(): Totals =
        transaction {
            Totals(
                days = BranchDayTable.selectAll().map { it[BranchDayTable.date] }.sorted(),
                shifts =
                    AttendanceTable.selectAll().associate {
                        it[AttendanceTable.id] to
                            (it[AttendanceTable.clockIn].toInstant() to it[AttendanceTable.clockOut]?.toInstant())
                    },
                shiftDays = AttendanceTable.selectAll().map { it[AttendanceTable.branchDayId] }.toSet(),
                clients = ClientTable.selectAll().count(),
                sessions =
                    SessionTable.selectAll().associate {
                        it[SessionTable.id] to "${it[SessionTable.sessionStatus]} ${it[SessionTable.finalPrice]}"
                    },
                sales = ProductSaleTable.selectAll().sumOf { it[ProductSaleTable.totalAmountAtTime] },
                expenses = ExpenseTable.selectAll().sumOf { it[ExpenseTable.amount] },
                moves = InventoryMovementTable.selectAll().count(),
                stock = stock(),
            )
        }

    private fun stock(): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where { BranchInventoryTable.branchId eq branchId }
                .single()[BranchInventoryTable.currentStock]
        }

    private fun createApp(): Javalin {
        // The built web app is not under test, and WEB_DIR may name a build that does not exist here.
        val config = AppConfig.parse().copy(webDir = null)
        JwtService.init(config)
        return Javalin.create { cfg ->
            configureHttpSurface(cfg, config)
            configureAuth(cfg)
            registerExceptionHandlers(cfg)
            registerServerErrorHandler(cfg)
            registerAllRoutes(cfg)
        }
    }

    companion object {
        private const val PHONES = 6
        private const val READERS = 2
        private const val THREADS = 8
        private const val ROUNDS = 4
        private const val COPIES = 4
        private const val STOCK = 100
        private const val SOLD = 2
        private const val RESTOCKED = 3
        private const val RESEND_EVERY = 3
        private const val LATE_START = 10
        private const val LATE_END = 17
        private const val BUSY = 503
        private const val BUSY_RETRIES = 50
        private const val BUSY_WAIT_MS = 100L
        private const val WAIT_S = 120L
        private const val BODY_SHOWN = 200
        private const val PRICE = "2500.00"
        private const val EXPENSE = "50.00"
        private val OK = 200..299
        private val ID = Regex("^\\{\"(?:id|attendanceId)\":\"([0-9a-f-]{36})\"")
        private val RECORDED = setOf("/api/sessions", "/api/product-sales", "/api/expenses")
    }
}
