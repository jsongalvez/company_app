@file:Suppress("LargeClass")

package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemittanceAuthzTest : BasePostgresTest() {
    private val submitUser = UUID.randomUUID()
    private val noneUser = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val otherBranchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val branchDayDate = LocalDate.of(2026, 7, 10)

    private lateinit var draftRemittanceId: UUID
    private lateinit var otherBranchDraftId: UUID
    private lateinit var submittedRemittanceId: UUID
    private lateinit var breakdownId: UUID
    private lateinit var otherBranchBreakdownId: UUID
    private lateinit var submittedBreakdownId: UUID
    private lateinit var sessionId: UUID
    private lateinit var productSaleId: UUID

    @Suppress("LongMethod")
    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(submitUser, "submit")
        trackOwned(AppUserTable, AppUserTable.id, submitUser)
        DatabaseTestHelper.insertTestUser(noneUser, "no-caps")
        trackOwned(AppUserTable, AppUserTable.id, noneUser)

        DatabaseTestHelper.insertTestBranch(branchId, "Remittance Branch $branchId")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Remittance Branch $otherBranchId")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.grantCapability(
            userId = submitUser,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, submitUser)

        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)

        sessionId = UUID.randomUUID()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = dayId,
        )
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)

        val catId = UUID.randomUUID()
        val prodId = UUID.randomUUID()
        DatabaseTestHelper.insertTestCategory(catId, "Cat $catId")
        DatabaseTestHelper.insertTestProduct(prodId, "Product $prodId", catId)
        productSaleId = UUID.randomUUID()
        DatabaseTestHelper.insertTestProductSale(
            id = productSaleId,
            branchDayId = dayId,
            productId = prodId,
            handledBy = submitUser,
        )
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, catId)
        trackOwned(ProductTable, ProductTable.id, prodId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId)

        draftRemittanceId = UUID.randomUUID()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = draftRemittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        breakdownId = UUID.randomUUID()
        RemittanceService.addDayBreakdown(submitUser, draftRemittanceId, breakdownId, dayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = UUID.randomUUID(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("100.00"),
        )
        trackOwned(RemittanceTable, RemittanceTable.id, draftRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, draftRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, draftRemittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, draftRemittanceId)

        otherBranchDraftId = UUID.randomUUID()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = otherBranchDraftId,
            type = RemittanceType.PRODUCT,
            branchId = otherBranchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        otherBranchBreakdownId = UUID.randomUUID()
        val otherDayId = DatabaseTestHelper.createBranchDayForDate(otherBranchId, branchDayDate)
        RemittanceService.addDayBreakdown(submitUser, otherBranchDraftId, otherBranchBreakdownId, otherDayId)
        trackOwned(RemittanceTable, RemittanceTable.id, otherBranchDraftId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, otherBranchDraftId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, otherBranchDraftId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, otherBranchDraftId)
        trackOwned(BranchDayTable, BranchDayTable.id, otherDayId)

        submittedRemittanceId = UUID.randomUUID()
        val subLineId = UUID.randomUUID()
        submittedBreakdownId = UUID.randomUUID()
        val secondSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = secondSessionId,
            clientId = clientId,
            branchDayId = dayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
        trackOwned(SessionTable, SessionTable.id, secondSessionId)
        RemittanceService.createDraft(
            callerId = submitUser,
            id = submittedRemittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        RemittanceService.addDayBreakdown(submitUser, submittedRemittanceId, submittedBreakdownId, dayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = submittedRemittanceId,
            id = subLineId,
            type = RemittanceLineType.SESSION,
            sessionId = secondSessionId,
            productSaleId = null,
            amount = BigDecimal("500.00"),
        )
        RemittanceService.submit(
            callerId = submitUser,
            remittanceId = submittedRemittanceId,
            expectedVersion = RemittanceService.getRemittance(submittedRemittanceId).remittance.version,
        )
        trackOwned(RemittanceTable, RemittanceTable.id, submittedRemittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, submittedRemittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, submittedRemittanceId)
        trackOwned(
            RemittanceFinancialSnapshotTable,
            RemittanceFinancialSnapshotTable.remittanceId,
            submittedRemittanceId,
        )

        trackOwned(AuditLogTable, AuditLogTable.changedBy, submitUser)
    }

    companion object {
        private val DEFAULT_USER = UUID.randomUUID()

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
                RemittanceRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    // ──────────────────────────────────────────────
    // GET /api/remittances (G1 list)
    // ──────────────────────────────────────────────

    @Test
    fun `GET list allowed with net for granted user`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/remittances?branchId=$branchId",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"netIncome\":\"500.00\""))
        }
    }

    @Test
    fun `GET list filtered by status`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/remittances?branchId=$branchId&status=SUBMITTED",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"status\":\"SUBMITTED\""))
            assertTrue(!body.contains("\"id\":\"$draftRemittanceId\""))
        }
    }

    @Test
    fun `GET list forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances?branchId=$branchId", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET list forbidden on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances?branchId=$otherBranchId", asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `GET list returns 404 for missing branch with grant`() {
        val missingBranchId = UUID.randomUUID()
        DatabaseTestHelper.grantCapability(
            userId = submitUser,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = missingBranchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, submitUser)
        testServer.client.let { client ->
            assertEquals(
                404,
                client.get("/api/remittances?branchId=$missingBranchId", asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `GET list returns 400 without branchId`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/remittances", asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `GET list accepts ALL status`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/remittances?branchId=$branchId&status=ALL",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"netIncome\":\"500.00\""))
            assertTrue(body.contains("\"id\":\"$draftRemittanceId\""))
        }
    }

    @Test
    fun `GET list returns 400 on invalid status`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client.get("/api/remittances?branchId=$branchId&status=NOPE", asUser(submitUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // G2/G3/G4 — pickers
    // ──────────────────────────────────────────────

    @Test
    fun `GET sessions picker allowed with client name`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branches/$branchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"clientName\":\"Test Client\""))
            assertTrue(body.contains("\"finalPrice\":\"2500.00\""))
        }
    }

    @Test
    fun `GET sessions picker forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .get(
                        "/api/branches/$branchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `GET product-sales picker allowed with product name`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branches/$branchId/remittance-product-sales?from=$rangeStart&to=$rangeEnd",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"productName\":"))
            assertTrue(body.contains("\"totalAmountAtTime\":\"100.00\""))
        }
    }

    @Test
    fun `GET product-sales picker forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .get(
                        "/api/branches/$branchId/remittance-product-sales?from=$rangeStart&to=$rangeEnd",
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `GET days picker allowed with effective statuses`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/branches/$branchId/remittance-days?from=$rangeStart&to=$rangeEnd",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"status\":\"REMITTED\""))
        }
    }

    @Test
    fun `GET days picker forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .get(
                        "/api/branches/$branchId/remittance-days?from=$rangeStart&to=$rangeEnd",
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `GET days picker returns 400 on reversed range`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .get(
                        "/api/branches/$branchId/remittance-days?from=$rangeEnd&to=$rangeStart",
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `GET days picker returns 400 on invalid date`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .get(
                        "/api/branches/$branchId/remittance-days?from=not-a-date&to=$rangeEnd",
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `GET pickers forbidden on other branch`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .get(
                        "/api/branches/$otherBranchId/remittance-sessions?from=$rangeStart&to=$rangeEnd",
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // G5 — DELETE day-breakdown
    // ──────────────────────────────────────────────

    @Test
    fun `DELETE day breakdown allowed for granted user`() {
        testServer.client.let { client ->
            val response =
                client.delete(
                    "/api/remittances/$draftRemittanceId/day-breakdowns/$breakdownId",
                    "",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"id\":\"$breakdownId\""))
        }
    }

    @Test
    fun `DELETE day breakdown forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .delete(
                        "/api/remittances/$draftRemittanceId/day-breakdowns/$breakdownId",
                        "",
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `DELETE day breakdown forbidden on other branch remittance`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .delete(
                        "/api/remittances/$otherBranchDraftId/day-breakdowns/$otherBranchBreakdownId",
                        "",
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `DELETE day breakdown on submitted remittance returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .delete(
                        "/api/remittances/$submittedRemittanceId/day-breakdowns/$submittedBreakdownId",
                        "",
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // G6/G7 — detail snapshot + drift
    // ──────────────────────────────────────────────

    @Test
    fun `GET detail includes snapshot for submitted SESSION remittance`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/remittances/$submittedRemittanceId",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"snapshot\":{\"remittanceId\":\"$submittedRemittanceId\""))
            assertTrue(body.contains("\"netIncome\":\"500.00\""))
        }
    }

    @Test
    fun `GET drift allowed for granted user`() {
        testServer.client.let { client ->
            val response =
                client.get(
                    "/api/remittances/$submittedRemittanceId/drift",
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"frozen\":"))
            assertTrue(body.contains("\"currentNet\":\"500.00\""))
        }
    }

    @Test
    fun `GET drift forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances/$submittedRemittanceId/drift", asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `GET drift returns 404 for draft without snapshot`() {
        testServer.client.let { client ->
            assertEquals(
                404,
                client.get("/api/remittances/$draftRemittanceId/drift", asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `GET drift returns 403 on other branch remittance`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client.get("/api/remittances/$otherBranchDraftId/drift", asUser(submitUser)).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // F9 regression — pre-existing sub-routes were ungated
    // ──────────────────────────────────────────────

    @Test
    fun `POST line forbidden for no-capability user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "SESSION",
                    "sessionId" to sessionId.toString(),
                    "amount" to "50.00",
                )
            assertEquals(
                403,
                client.post("/api/remittances/$draftRemittanceId/lines", body, asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `POST line duplicate session in same draft returns 409`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "SESSION",
                    "sessionId" to sessionId.toString(),
                    "amount" to "50.00",
                )
            assertEquals(
                409,
                client.post("/api/remittances/$draftRemittanceId/lines", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `POST line still allowed for granted user`() {
        val freshSessionId = UUID.randomUUID()
        val freshDayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        DatabaseTestHelper.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
        trackOwned(SessionTable, SessionTable.id, freshSessionId)
        trackOwned(BranchDayTable, BranchDayTable.id, freshDayId)
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "SESSION",
                    "sessionId" to freshSessionId.toString(),
                    "amount" to "50.00",
                )
            assertEquals(
                201,
                client.post("/api/remittances/$draftRemittanceId/lines", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `DELETE line forbidden for no-capability user`() {
        val lineId = UUID.randomUUID()
        val freshSessionId = UUID.randomUUID()
        val freshDayId = DatabaseTestHelper.createBranchDayForDate(branchId, branchDayDate)
        DatabaseTestHelper.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
        trackOwned(SessionTable, SessionTable.id, freshSessionId)
        trackOwned(BranchDayTable, BranchDayTable.id, freshDayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = freshSessionId,
            productSaleId = null,
            amount = BigDecimal("75.00"),
        )
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, draftRemittanceId)
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .delete(
                        "/api/remittances/$draftRemittanceId/lines/$lineId",
                        "",
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST day-breakdown forbidden for no-capability user`() {
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, LocalDate.of(2026, 7, 11))
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "branchDayId" to dayId.toString(),
                )
            assertEquals(
                403,
                client.post("/api/remittances/$draftRemittanceId/day-breakdowns", body, asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `POST submit forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/remittances/$draftRemittanceId/submit",
                        mapOf("expectedVersion" to 2),
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    // ──────────────────────────────────────────────
    // G8 — undo + header PATCH
    // ──────────────────────────────────────────────

    @Test
    fun `POST undo allowed for granted user`() {
        testServer.client.let { client ->
            val version = RemittanceService.getRemittance(submittedRemittanceId).remittance.version
            val response =
                client.post(
                    "/api/remittances/$submittedRemittanceId/undo",
                    mapOf("expectedVersion" to version, "reason" to "wrong amounts"),
                    asUser(submitUser),
                )
            assertEquals(200, response.code)
            val body = response.body.string().orEmpty()
            assertTrue(body.contains("\"status\":\"DRAFT\""))
        }
    }

    @Test
    fun `POST undo forbidden for no-capability user`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/remittances/$submittedRemittanceId/undo",
                        mapOf("expectedVersion" to 3, "reason" to "wrong"),
                        asUser(noneUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST undo forbidden on other branch remittance`() {
        testServer.client.let { client ->
            assertEquals(
                403,
                client
                    .post(
                        "/api/remittances/$otherBranchDraftId/undo",
                        mapOf("expectedVersion" to 1, "reason" to "wrong"),
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST undo on non-submitted remittance returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$draftRemittanceId/undo",
                        mapOf("expectedVersion" to 2, "reason" to "wrong"),
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST undo without reason returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$submittedRemittanceId/undo",
                        mapOf("expectedVersion" to 3, "reason" to "   "),
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST undo with multi-line reason returns 400`() {
        testServer.client.let { client ->
            assertEquals(
                400,
                client
                    .post(
                        "/api/remittances/$submittedRemittanceId/undo",
                        mapOf("expectedVersion" to 3, "reason" to "line one\nline two"),
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `POST undo with version mismatch returns 409`() {
        testServer.client.let { client ->
            assertEquals(
                409,
                client
                    .post(
                        "/api/remittances/$submittedRemittanceId/undo",
                        mapOf("expectedVersion" to 99, "reason" to "wrong"),
                        asUser(submitUser),
                    ).code,
            )
        }
    }

    @Test
    fun `PATCH header allowed for granted user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "PRODUCT",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 2,
                )
            val response = client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser))
            assertEquals(200, response.code)
            val responseBody = response.body.string().orEmpty()
            assertTrue(responseBody.contains("\"method\":\"HANDED_TO_ACCOUNTANT\""))
            assertTrue(responseBody.contains("\"version\":3"))
        }
    }

    @Test
    fun `PATCH header to a type already used for the submitted date returns 409`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 2,
                )
            assertEquals(
                409,
                client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header forbidden for no-capability user`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 2,
                )
            assertEquals(
                403,
                client.patch("/api/remittances/$draftRemittanceId", body, asUser(noneUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header forbidden on other branch remittance`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 1,
                )
            assertEquals(
                403,
                client.patch("/api/remittances/$otherBranchDraftId", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header invalid type returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "NOPE",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 2,
                )
            assertEquals(
                400,
                client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header reversed range returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeEnd.toString(),
                    "dateRangeEnd" to rangeStart.toString(),
                    "expectedVersion" to 2,
                )
            assertEquals(
                400,
                client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header on submitted remittance returns 400`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 3,
                )
            assertEquals(
                400,
                client.patch("/api/remittances/$submittedRemittanceId", body, asUser(submitUser)).code,
            )
        }
    }

    @Test
    fun `PATCH header with version mismatch returns 409`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 99,
                )
            assertEquals(
                409,
                client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser)).code,
            )
        }
    }
}
