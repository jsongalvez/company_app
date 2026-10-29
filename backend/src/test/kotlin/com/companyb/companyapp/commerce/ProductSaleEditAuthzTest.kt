package com.companyb.companyapp.commerce

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.audit.AuditLogRoutes
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.ExpenseRoutes
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.reporting.DailySalesSummaryView
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import io.javalin.testtools.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.math.BigDecimal
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owed #21 (user 12952): "wait, you cant edit product sales?" Product sales follow the expense
 * rules: every branch member edits, deletes and restores them on a day that is not remitted, with
 * a reason on a past day; a remitted day stays with EDIT_PAST_DAY holders. Stock follows every
 * change, and the change history shows who, what and why. "4 yes": an expense goes on an earlier
 * day that has nothing recorded yet.
 */
class ProductSaleEditAuthzTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val members = MEMBER_ROLES.associateWith { TestFixtures.uuid() }
    private val coordinator = TestFixtures.uuid()
    private val relief = TestFixtures.uuid()
    private val onboarding = TestFixtures.uuid()
    private val soap = TestFixtures.uuid()
    private val oil = TestFixtures.uuid()
    private lateinit var todayId: UUID
    private lateinit var yesterdayId: UUID
    private lateinit var remittedId: UUID

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        todayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today)
        yesterdayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        remittedId = BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today.minusDays(2))
        (members + ("COORDINATOR" to coordinator)).forEach { (role, id) ->
            IdentityFixtures.insertTestUser(id, role.lowercase())
            assignRole(id, role)
            BranchWorkforceFixtures.insertTestAssignment(userId = id, branchId = branchId, slot = 1, assignedBy = id)
        }
        IdentityFixtures.insertTestUser(onboarding, "onboarding")
        assignRole(onboarding, "ONBOARDING")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = onboarding,
            branchId = branchId,
            slot = 2,
            assignedBy = onboarding,
        )
        IdentityFixtures.insertTestUser(relief, "relief")
        assignRole(relief, "PRACTITIONER")
        IdentityFixtures.grantCapability(
            relief,
            CapabilityCodes.EDIT_BRANCH_DATA,
            CapabilityContextType.BRANCH_DAY,
            todayId,
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
        CommerceFinanceFixtures.insertTestProduct(
            oil,
            name = "Oil",
            categoryId = category,
            unitPrice = BigDecimal("250.00"),
        )
        CommerceFinanceFixtures.insertTestCard(branchId, soap, STOCK)
        CommerceFinanceFixtures.insertTestCard(branchId, oil, STOCK)
    }

    @Test
    fun `every member changes the quantity of a sale today and stock follows`() =
        forEachMember { who ->
            val before = stock(soap)
            val (id, v) = sell(todayId, who, quantity = 2)
            assertEquals(before - 2, stock(soap), "sold 2")
            val r = edit(id to v, quantity = 3, reason = null, who = who)
            assertCode(200, r, who)
            assertEquals("300.00", json(r)["totalAmountAtTime"]?.jsonPrimitive?.content)
            assertEquals(before - 3, stock(soap), "edited to 3")
        }

    @Test
    fun `changing the product returns the old stock and takes the new at its price`() {
        val who = members.getValue("PRACTITIONER")
        val soapBefore = stock(soap)
        val oilBefore = stock(oil)
        val (id, v) = sell(todayId, who, quantity = 2)
        val r = edit(id to v, quantity = 1, productId = oil, reason = null, who = who)
        assertCode(200, r, who)
        assertEquals("Oil", json(r)["productName"]?.jsonPrimitive?.content)
        assertEquals("250.00", json(r)["totalAmountAtTime"]?.jsonPrimitive?.content)
        assertEquals(soapBefore, stock(soap))
        assertEquals(oilBefore - 1, stock(oil))
    }

    @Test
    fun `a delete puts the stock back and drops the sale from the day total, a restore takes it again`() =
        forEachMember { who ->
            val before = stock(soap)
            val totalBefore = dayProductTotal(todayId)
            val (id, v) = sell(todayId, who, quantity = 2)
            assertCode(200, delete(id, v, "double entry", who), who)
            assertEquals(before, stock(soap), "stock back after delete")
            assertEquals(0, totalBefore.compareTo(dayProductTotal(todayId)), "day total drops the deleted sale")
            val restored = post("${ApiRoutes.PRODUCT_SALES}/$id/restore", mapOf("reason" to null), who)
            assertCode(200, restored, who)
            assertEquals(before - 2, stock(soap), "stock taken again after restore")
        }

    @Test
    fun `a past unremitted day takes a member's fix with a reason only`() =
        forEachMember { who ->
            val (id, v) = sell(yesterdayId, who, quantity = 1)
            assertCode(400, edit(id to v, quantity = 2, reason = null, who = who), who)
            assertCode(200, edit(id to v, quantity = 2, reason = "typo", who = who), who)
            assertCode(400, delete(id, v + 1, "", who), who)
            assertCode(200, delete(id, v + 1, "double entry", who), who)
        }

    @Test
    fun `a remitted day stays locked for members, open to Coordinators`() {
        val sale = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(sale, remittedId, soap, coordinator)
        forEachMember { who ->
            assertCode(403, edit(sale.toString() to 1, quantity = 2, reason = "typo", who = who), who)
            assertCode(403, delete(sale.toString(), 1, "double", who), who)
        }
        assertCode(200, edit(sale.toString() to 1, quantity = 2, reason = "typo", who = coordinator), "COORDINATOR")
    }

    @Test
    fun `relief edits sales on its own day, onboarding gets nothing`() {
        val (id, v) = sell(todayId, members.getValue("OWNER"), quantity = 1)
        assertCode(200, edit(id to v, quantity = 2, reason = null, who = relief), "relief")
        assertCode(403, edit(id to v + 1, quantity = 3, reason = null, who = onboarding), "onboarding")
        assertCode(403, get("${ApiRoutes.PRODUCT_SALES}?branchDayId=$todayId", onboarding), "onboarding")
    }

    @Test
    fun `the day's sales list shows deleted ones marked, with their reason`() {
        val who = members.getValue("ACCOUNTANT")
        val (kept, _) = sell(todayId, who, quantity = 1)
        val (gone, v) = sell(todayId, who, quantity = 1)
        assertCode(200, delete(gone, v, "wrong product", who), who)
        val r = get("${ApiRoutes.PRODUCT_SALES}?branchDayId=$todayId", who)
        assertCode(200, r, who)
        val rows = Json.parseToJsonElement(r.body.string()).jsonArray.map { it.jsonObject }
        val byId = rows.associateBy { it["id"]?.jsonPrimitive?.content }
        assertEquals("null", byId.getValue(kept)["deletedAt"].toString())
        assertEquals("wrong product", byId.getValue(gone)["deletedReason"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a sale's change history shows each edit with who and why`() {
        val owner = members.getValue("OWNER")
        val (id, v) = sell(yesterdayId, owner, quantity = 1)
        assertCode(200, edit(id to v, quantity = 2, reason = "counted again", who = owner), "OWNER")
        val r = get("${ApiRoutes.AUDIT_LOG}?tableName=product_sale&recordId=$id", owner)
        assertCode(200, r, "OWNER")
        val rows = Json.parseToJsonElement(r.body.string()).jsonArray.map { it.jsonObject }
        val edit = rows.first { it["reason"]?.jsonPrimitive?.content == "counted again" }
        assertEquals(owner.toString(), edit["changedBy"]?.jsonPrimitive?.content)
        assertEquals("UPDATE", edit["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun `every member adds an expense to an earlier day with nothing recorded yet`() =
        forEachMember { who ->
            val date = TestFixtures.today.minusDays(5L + MEMBER_ROLES.indexOf(roleOf(who)))
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchId" to branchId.toString(),
                    "date" to date.toString(),
                    "amount" to "50.00",
                    "category" to "PANTRY",
                )
            val r = post(ApiRoutes.EXPENSES, body, who)
            assertCode(201, r, who)
            val dayId = UUID.fromString(json(r)["branchDayId"]?.jsonPrimitive?.content)
            val dayDate =
                transaction {
                    BranchDayTable.selectAll().where { BranchDayTable.id eq dayId }.single()[BranchDayTable.date]
                }
            assertEquals(date, dayDate)
        }

    @Test
    fun `an expense by date stays out of a later day and away from onboarding`() {
        val body = { date: java.time.LocalDate ->
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "branchId" to branchId.toString(),
                "date" to date.toString(),
                "amount" to "50.00",
                "category" to "PANTRY",
            )
        }
        assertCode(400, post(ApiRoutes.EXPENSES, body(TestFixtures.today.plusDays(1)), coordinator), "later day")
        assertCode(403, post(ApiRoutes.EXPENSES, body(TestFixtures.today.minusDays(9)), onboarding), "onboarding")
    }

    private fun roleOf(id: UUID) = members.entries.first { it.value == id }.key

    private fun stock(product: UUID): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where { (BranchInventoryTable.branchId eq branchId) and (BranchInventoryTable.productId eq product) }
                .single()[BranchInventoryTable.currentStock]
        }

    private fun dayProductTotal(dayId: UUID): BigDecimal =
        transaction {
            DailySalesSummaryView
                .selectAll()
                .where { DailySalesSummaryView.branchDayId eq dayId }
                .single()[DailySalesSummaryView.totalProductSales]
        }

    private fun sell(
        dayId: UUID,
        who: UUID,
        quantity: Int,
    ): Pair<String, Int> {
        val r =
            post(
                ApiRoutes.PRODUCT_SALES,
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "branchDayId" to dayId.toString(),
                    "isWalkIn" to true,
                    "productId" to soap.toString(),
                    "quantity" to quantity,
                ),
                who,
            )
        val body = r.body.string()
        assertEquals(201, r.code, "sell: $body")
        val j = Json.parseToJsonElement(body).jsonObject
        return j.getValue("id").jsonPrimitive.content to j.getValue("version").jsonPrimitive.int
    }

    private fun edit(
        sale: Pair<String, Int>,
        quantity: Int,
        productId: UUID = soap,
        reason: String?,
        who: Any,
    ) = testServer.client.patch(
        "${ApiRoutes.PRODUCT_SALES}/${sale.first}",
        mapOf(
            "quantity" to quantity,
            "productId" to productId.toString(),
            "expectedVersion" to sale.second,
            "reason" to reason,
        ),
        asUser(who),
    )

    private fun delete(
        id: String,
        version: Int,
        reason: String,
        who: UUID,
    ) = testServer.client.delete(
        "${ApiRoutes.PRODUCT_SALES}/$id",
        mapOf("reason" to reason, "expectedVersion" to version),
        asUser(who),
    )

    private fun json(r: Response) = Json.parseToJsonElement(r.body.string()).jsonObject

    private fun forEachMember(block: (UUID) -> Unit) {
        val failures =
            members.mapNotNull { (role, id) ->
                runCatching { block(id) }.exceptionOrNull()?.let { "$role: ${it.message}" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun assertCode(
        expected: Int,
        r: Response,
        who: Any,
    ) = assertEquals(expected, r.code, "$who: ${r.body.string()}")

    private fun post(
        path: String,
        body: Any,
        who: UUID,
    ) = testServer.client.post(path, body, asUser(who))

    private fun get(
        path: String,
        who: UUID,
    ) = testServer.client.get(path, asUser(who))

    private fun asUser(id: Any) = Consumer<Request.Builder> { it.header("X-Test-User", id.toString()) }

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

    companion object {
        private const val STOCK = 500
        private val MEMBER_ROLES = listOf("OWNER", "ACCOUNTANT", "PRACTITIONER")
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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to e.message))
                }
                ProductSaleRoutes.register(cfg)
                ExpenseRoutes.register(cfg)
                AuditLogRoutes.register(cfg)
            }
        }
    }
}
