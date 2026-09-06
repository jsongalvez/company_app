@file:Suppress("LargeClass")

package com.companyb.companyapp.remittance
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.remittance.RemittanceService
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
    private val submitUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

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
        IdentityFixtures.insertTestUser(submitUser, "submit")
        IdentityFixtures.insertTestUser(noneUser, "no-caps")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Remittance Branch $branchId")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Remittance Branch $otherBranchId")

        IdentityFixtures.grantCapability(
            userId = submitUser,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )

        SessionClientFixtures.insertTestClient(clientId)

        sessionId = TestFixtures.uuid()
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = dayId,
        )

        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat $catId")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Product $prodId", catId)
        productSaleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = productSaleId,
            branchDayId = dayId,
            productId = prodId,
            handledBy = submitUser,
        )

        draftRemittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = draftRemittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        breakdownId = TestFixtures.uuid()
        RemittanceService.addDayBreakdown(submitUser, draftRemittanceId, breakdownId, dayId)
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = BigDecimal("100.00"),
        )

        otherBranchDraftId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = submitUser,
            id = otherBranchDraftId,
            type = RemittanceType.PRODUCT,
            branchId = otherBranchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        otherBranchBreakdownId = TestFixtures.uuid()
        val otherDayId = BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, branchDayDate)
        RemittanceService.addDayBreakdown(submitUser, otherBranchDraftId, otherBranchBreakdownId, otherDayId)

        submittedRemittanceId = TestFixtures.uuid()
        val subLineId = TestFixtures.uuid()
        submittedBreakdownId = TestFixtures.uuid()
        val secondSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = secondSessionId,
            clientId = clientId,
            branchDayId = dayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
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
    }

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
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
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
                RemittancePickerRoutes.register(cfg)
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
        val missingBranchId = TestFixtures.uuid()
        IdentityFixtures.grantCapability(
            userId = submitUser,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = missingBranchId,
            sourceId = sourceId,
        )
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
                    "id" to TestFixtures.uuid().toString(),
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
                    "id" to TestFixtures.uuid().toString(),
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
        val freshSessionId = TestFixtures.uuid()
        val freshDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
        val lineId = TestFixtures.uuid()
        val freshSessionId = TestFixtures.uuid()
        val freshDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        SessionClientFixtures.insertTestSession(
            id = freshSessionId,
            clientId = clientId,
            branchDayId = freshDayId,
            sessionStatus = com.companyb.companyapp.domain.SessionStatus.COMPLETED,
        )
        RemittanceService.addLine(
            callerId = submitUser,
            remittanceId = draftRemittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = freshSessionId,
            productSaleId = null,
            amount = BigDecimal("75.00"),
        )
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
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, LocalDate.of(2026, 7, 11))
        testServer.client.let { client ->
            val body =
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
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
                    "expectedVersion" to 3,
                )
            val response = client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser))
            assertEquals(200, response.code)
            val responseBody = response.body.string().orEmpty()
            assertTrue(responseBody.contains("\"method\":\"HANDED_TO_ACCOUNTANT\""))
            assertTrue(responseBody.contains("\"version\":4"))
        }
    }

    @Test
    fun `PATCH header to a type already used for the submitted date is allowed for drafts`() {
        testServer.client.let { client ->
            val body =
                mapOf(
                    "type" to "SESSION",
                    "method" to "HANDED_TO_ACCOUNTANT",
                    "dateRangeStart" to rangeStart.toString(),
                    "dateRangeEnd" to rangeEnd.toString(),
                    "expectedVersion" to 3,
                )
            assertEquals(200, client.patch("/api/remittances/$draftRemittanceId", body, asUser(submitUser)).code)
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
